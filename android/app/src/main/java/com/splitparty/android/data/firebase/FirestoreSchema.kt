package com.splitparty.android.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyMember
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.split.Tip

/** Collection and field names from docs/firestore-schema.md. */
internal object FirestoreSchema {
    const val USERS = "users"
    const val TRANSACTIONS = "transactions"
    const val PARTIES = "parties"
    const val JOIN_CODES = "joinCodes"
}

internal fun DocumentSnapshot.toUser(): User? = if (!exists()) {
    null
} else {
    User(
        id = id,
        username = getString("username") ?: id,
        displayName = getString("displayName") ?: Username.fallbackName(id),
        avatar = getString("avatar").orEmpty(),
        balanceCents = getLong("balanceCents") ?: 0L,
        friendIds = (get("friendIds") as? List<*>).orEmpty().filterIsInstance<String>()
    )
}

internal fun DocumentSnapshot.toTransaction(): Transaction = Transaction(
    id = id,
    payerId = getString("payerId").orEmpty(),
    payeeId = getString("payeeId").orEmpty(),
    amountCents = getLong("amountCents") ?: 0L,
    memo = getString("memo").orEmpty(),
    merchantName = getString("merchantName"),
    partyId = getString("partyId"),
    createdAtMillis = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
    likesCount = getLong("likesCount") ?: 0L,
    commentsCount = getLong("commentsCount") ?: 0L
)

/** Null when missing or not shaped like a party (it then never shows anywhere). */
internal fun DocumentSnapshot.toParty(): Party? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.time
    val expiresAt = getTimestamp("expiresAt")?.toDate()?.time
    if (!exists() || createdAt == null || expiresAt == null) return null
    val memberMaps = get("members") as? Map<*, *> ?: emptyMap<Any, Any>()
    val members = (get("memberIds") as? List<*>).orEmpty().filterIsInstance<String>().map { uid ->
        val member = memberMaps[uid] as? Map<*, *>
        val joinedAt = getTimestamp("members.$uid.joinedAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        PartyMember(uid, joinedAt, member?.get("paid") as? Boolean ?: false)
    }
    val attempts = (get("wrongCodeAttempts") as? Map<*, *>).orEmpty()
        .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toLong() } } }
        .toMap()
    return Party(
        id = id,
        hostId = getString("hostId").orEmpty(),
        merchantId = getString("merchantId").orEmpty(),
        joinCode = (get("joinCode") as? List<*>).orEmpty().filterIsInstance<String>(),
        status = PartyStatus.fromWire(getString("status")),
        createdAtMillis = createdAt,
        expiresAtMillis = expiresAt,
        members = members,
        wrongCodeAttempts = attempts,
        billCents = (get("billCents") as? Number)?.toLong()?.takeIf { it >= 0 },
        tip = (get("tip") as? Map<*, *>)?.toTip()
    )
}

/** Unknown or malformed shapes read as no tip (0%). */
private fun Map<*, *>.toTip(): Tip? = when (get("type")) {
    "percent" -> (get("percent") as? Number)?.toInt()?.takeIf { it >= 0 }?.let { Tip.Percent(it) }
    "custom" -> (get("amountCents") as? Number)?.toLong()?.takeIf { it >= 0 }?.let { Tip.Custom(it) }
    else -> null
}

/** `parties.tip` as written: exactly the schema's two shapes. */
internal fun Tip.toFirestore(): Map<String, Any> = when (this) {
    is Tip.Percent -> mapOf("type" to "percent", "percent" to percent.toLong())
    is Tip.Custom -> mapOf("type" to "custom", "amountCents" to amountCents)
}
