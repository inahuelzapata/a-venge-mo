package com.splitparty.android.domain.model

import com.splitparty.android.domain.split.CustomShareProblem
import com.splitparty.android.domain.split.Split
import com.splitparty.android.domain.split.SplitResult
import com.splitparty.android.domain.split.Tip

/**
 * `parties.status` (docs/firestore-schema.md). [Cancelled] (host only) is its own
 * state, never shown as expired or paid. Unknown values read as [Expired], so they never block anyone.
 */
enum class PartyStatus(val wire: String) {
    Open("open"),
    Paid("paid"),
    Expired("expired"),
    Cancelled("cancelled");

    companion object {
        fun fromWire(value: String?): PartyStatus = entries.firstOrNull { it.wire == value } ?: Expired
    }
}

/**
 * One entry of `parties.members`, in `memberIds` order. [customShareCents]: the
 * host's custom amount for this member; null = on the equal split of the rest.
 */
data class PartyMember(val userId: String, val joinedAtMillis: Long?, val paid: Boolean, val customShareCents: Long? = null)

/**
 * A `parties/{partyId}` document, the fields the apps read. The per-person
 * share is never stored: [split] derives it from bill, tip, headcount and the
 * host's custom amounts.
 */
data class Party(
    val id: String,
    val hostId: String,
    val merchantId: String,
    val joinCode: List<String>,
    val status: PartyStatus,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    /** Host first, then joinees in join order. */
    val members: List<PartyMember>,
    val wrongCodeAttempts: Map<String, Long>,
    /** Pre-tip bill in cents; null until the host confirms one. */
    val billCents: Long? = null,
    /** Null until chosen (reads as 0%). */
    val tip: Tip? = null,
    /** When the host cancelled; null unless [PartyStatus.Cancelled] (or while the server time is pending). */
    val cancelledAtMillis: Long? = null
) {
    val memberIds: List<String> get() = members.map { it.userId }

    /**
     * Joinable/visible: stored `open` AND not past `expiresAt` by this phone's
     * clock. The stored status may lag (nobody has written `expired` yet), so
     * the clock always wins.
     */
    fun isActiveAt(nowMillis: Long): Boolean = status == PartyStatus.Open && nowMillis < expiresAtMillis

    val isCancelled: Boolean get() = status == PartyStatus.Cancelled

    /** Only the host, only while the party is open and unexpired (docs/firestore-schema.md "Cancelling a party"). */
    fun canCancel(userId: String, nowMillis: Long): Boolean = userId == hostId && isActiveAt(nowMillis)

    fun hasMember(userId: String): Boolean = members.any { it.userId == userId }

    fun wrongAttemptsOf(userId: String): Long = wrongCodeAttempts[userId] ?: 0L

    /** Three wrong picks lock that user out of this party for good. */
    fun isLockedOut(userId: String): Boolean = wrongAttemptsOf(userId) >= MAX_WRONG_CODE_ATTEMPTS

    /**
     * Total, tip and shares in [memberIds] order, custom amounts included; null
     * until a bill exists. Check [SplitResult.fits] before settling.
     */
    val split: SplitResult? get() = billCents?.takeIf {
        members.isNotEmpty()
    }?.let { Split.compute(it, tip, members.map { m -> m.customShareCents }) }

    fun shareOf(userId: String): Long? = split?.shares?.getOrNull(memberIds.indexOf(userId))

    val hasCustomShares: Boolean get() = members.any { it.customShareCents != null }

    fun customShareOf(userId: String): Long? = members.firstOrNull { it.userId == userId }?.customShareCents

    /**
     * What would be wrong with the custom amounts if [userId]'s were [cents]
     * (null = back to the equal split); null when they fit, or while there is
     * no total to check against.
     */
    fun customShareProblemWith(userId: String, cents: Long?): CustomShareProblem? {
        val bill = billCents ?: return null
        val amounts = members.map { if (it.userId == userId) cents else it.customShareCents }
        return Split.problem(bill + Split.tipCents(bill, tip), amounts)
    }

    /** Everyone but the host, in join order: the members Pay settles. */
    val joinees: List<PartyMember> get() = members.drop(1)

    /** What blocks Pay right now, or null when the host can settle. */
    fun payBlockerAt(nowMillis: Long): PayBlocker? = when {
        status == PartyStatus.Paid -> PayBlocker.AlreadyPaid
        status == PartyStatus.Cancelled -> PayBlocker.Cancelled
        !isActiveAt(nowMillis) -> PayBlocker.Expired
        billCents == null -> PayBlocker.NoTotal
        joinees.isEmpty() -> PayBlocker.NoMembers
        split?.fits == false -> PayBlocker.CustomSharesDontFit
        else -> null
    }

    fun millisLeftAt(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    companion object {
        const val TTL_MILLIS = 5 * 60 * 1000L
        const val MAX_WRONG_CODE_ATTEMPTS = 3L

        /** PoC cap from the rules (they cannot loop over a list). */
        const val MAX_MEMBERS = 8
    }
}

/** Why the host cannot tap Pay yet. [CustomSharesDontFit]: the custom amounts no longer fit the total. */
enum class PayBlocker { AlreadyPaid, Cancelled, Expired, NoTotal, NoMembers, CustomSharesDontFit }

/** A cancel this phone committed: the fields of the `party_cancelled` analytics event. */
data class PartyCancellation(val partyId: String, val memberCount: Int, val secondsSinceCreated: Long)

/** The one seeded merchant (backend/lib/seedData.js). The demo switch stands in for its geofence. */
object DemoMerchant {
    const val ID = "tonys-greenwich"
    const val NAME = "Tony's"
}
