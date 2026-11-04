package com.splitparty.android.domain.model

/**
 * Domain shapes of the documents in docs/firestore-schema.md. Only the fields
 * this slice needs; nothing is written from them. Money is always a Long number
 * of cents, never a floating-point value.
 */
data class User(val id: String, val username: String, val displayName: String, val avatar: String, val balanceCents: Long)

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
    val commentsCount: Long
)
