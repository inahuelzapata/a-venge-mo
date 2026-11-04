package com.splitparty.android.domain.repository

import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.UserLookup
import kotlinx.coroutines.flow.Flow

/** Read access to the shared demo database (docs/firestore-schema.md). Listeners restart by themselves after a failure. */
interface SplitPartyRepository {
    /** Username-only login: asks the server (never a cache) whether `users/{normalizedUsername}` exists. */
    suspend fun findUser(normalizedUsername: String): UserLookup

    /** Newest 50 transactions of everyone. */
    fun feed(): Flow<LiveEvent<List<Transaction>>>

    /** Newest 50 transactions where [userId] is payer or payee. */
    fun transactionsOf(userId: String): Flow<LiveEvent<List<Transaction>>>

    /** `users/{userId}`; null when the document does not exist. */
    fun user(userId: String): Flow<LiveEvent<User?>>

    /** All users by id, for display names and avatars. */
    fun users(): Flow<LiveEvent<Map<String, User>>>
}
