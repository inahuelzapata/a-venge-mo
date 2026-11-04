package com.splitparty.android.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username

/** Collection and field names from docs/firestore-schema.md. */
internal object FirestoreSchema {
    const val USERS = "users"
    const val TRANSACTIONS = "transactions"
}

internal fun DocumentSnapshot.toUser(): User? = if (!exists()) {
    null
} else {
    User(
        id = id,
        username = getString("username") ?: id,
        displayName = getString("displayName") ?: Username.fallbackName(id),
        avatar = getString("avatar").orEmpty(),
        balanceCents = getLong("balanceCents") ?: 0L
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
