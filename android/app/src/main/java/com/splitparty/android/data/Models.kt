package com.splitparty.android.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import java.util.Locale

/**
 * Field names and shapes come from docs/firestore-schema.md. Only the fields
 * this slice needs are read; nothing is ever written from these models.
 * Money is always a Long number of cents.
 */
object Collections {
    const val USERS = "users"
    const val TRANSACTIONS = "transactions"
}

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val balanceCents: Long,
)

data class Transaction(
    val id: String,
    val payerId: String,
    val payeeId: String,
    val amountCents: Long,
    val memo: String,
    val merchantName: String?,
    val partyId: String?,
    /** Epoch millis; estimated locally while a serverTimestamp() write is pending. */
    val createdAtMillis: Long?,
    val likesCount: Long,
    val commentsCount: Long,
)

fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    return User(
        id = id,
        username = getString("username") ?: id,
        displayName = getString("displayName") ?: Username.fallbackName(id),
        avatar = getString("avatar") ?: "",
        balanceCents = getLong("balanceCents") ?: 0L,
    )
}

fun DocumentSnapshot.toTransaction(): Transaction = Transaction(
    id = id,
    payerId = getString("payerId") ?: "",
    payeeId = getString("payeeId") ?: "",
    amountCents = getLong("amountCents") ?: 0L,
    memo = getString("memo") ?: "",
    merchantName = getString("merchantName"),
    partyId = getString("partyId"),
    createdAtMillis = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
    likesCount = getLong("likesCount") ?: 0L,
    commentsCount = getLong("commentsCount") ?: 0L,
)

/** Username rules from the schema: doc id == lower-case username. */
object Username {
    private val VALID = Regex("^[a-z0-9_]{3,20}$")

    /** What login looks up: `users/{username.trim().toLowerCase()}`. */
    fun normalize(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    /** Rejecting invalid ids up front also keeps "/" out of the document path. */
    fun isValid(id: String): Boolean = VALID.matches(id)

    fun fallbackName(id: String): String = id.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
