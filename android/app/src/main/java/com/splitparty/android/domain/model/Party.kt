package com.splitparty.android.domain.model

/** `parties.status` (docs/firestore-schema.md). Unknown values read as [Expired], so they never block anyone. */
enum class PartyStatus(val wire: String) {
    Open("open"),
    Paid("paid"),
    Expired("expired");

    companion object {
        fun fromWire(value: String?): PartyStatus = entries.firstOrNull { it.wire == value } ?: Expired
    }
}

/** One entry of `parties.members`, in `memberIds` order. */
data class PartyMember(val userId: String, val joinedAtMillis: Long?, val paid: Boolean)

/**
 * A `parties/{partyId}` document, the fields this slice reads. Bill and tip
 * belong to the total slice and are not read here.
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
    val wrongCodeAttempts: Map<String, Long>
) {
    val memberIds: List<String> get() = members.map { it.userId }

    /**
     * Joinable/visible: stored `open` AND not past `expiresAt` by this phone's
     * clock. The stored status may lag (nobody has written `expired` yet), so
     * the clock always wins.
     */
    fun isActiveAt(nowMillis: Long): Boolean = status == PartyStatus.Open && nowMillis < expiresAtMillis

    fun hasMember(userId: String): Boolean = members.any { it.userId == userId }

    fun wrongAttemptsOf(userId: String): Long = wrongCodeAttempts[userId] ?: 0L

    /** Three wrong picks lock that user out of this party for good. */
    fun isLockedOut(userId: String): Boolean = wrongAttemptsOf(userId) >= MAX_WRONG_CODE_ATTEMPTS

    fun millisLeftAt(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    companion object {
        const val TTL_MILLIS = 5 * 60 * 1000L
        const val MAX_WRONG_CODE_ATTEMPTS = 3L

        /** PoC cap from the rules (they cannot loop over a list). */
        const val MAX_MEMBERS = 8
    }
}

/** The one seeded merchant (backend/lib/seedData.js). The demo switch stands in for its geofence. */
object DemoMerchant {
    const val ID = "tonys-greenwich"
    const val NAME = "Tony's"
}
