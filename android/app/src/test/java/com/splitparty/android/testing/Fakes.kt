package com.splitparty.android.testing

import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.UserLookup
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SplitPartyRepository]: tests push listener events into the public flows. */
class FakeSplitPartyRepository(knownUsers: List<User> = TestData.users) : SplitPartyRepository {
    val accounts = knownUsers.associateBy { it.id }.toMutableMap()

    /** When set, every lookup returns this instead of searching [accounts]. */
    var lookupOverride: UserLookup? = null

    /** When set, lookups suspend until it completes (to observe the busy state). */
    var lookupGate: CompletableDeferred<Unit>? = null
    val lookups = mutableListOf<String>()

    val feed = MutableStateFlow<LiveEvent<List<Transaction>>>(LiveEvent.Pending)
    val userTransactions = MutableStateFlow<LiveEvent<List<Transaction>>>(LiveEvent.Pending)
    val user = MutableStateFlow<LiveEvent<User?>>(LiveEvent.Pending)
    val users = MutableStateFlow<LiveEvent<Map<String, User>>>(LiveEvent.Pending)

    override suspend fun findUser(normalizedUsername: String): UserLookup {
        lookups += normalizedUsername
        lookupGate?.await()
        return lookupOverride ?: accounts[normalizedUsername]?.let(UserLookup::Found) ?: UserLookup.NotFound
    }

    override fun feed(): Flow<LiveEvent<List<Transaction>>> = feed

    override fun transactionsOf(userId: String): Flow<LiveEvent<List<Transaction>>> = userTransactions

    override fun user(userId: String): Flow<LiveEvent<User?>> = user

    override fun users(): Flow<LiveEvent<Map<String, User>>> = users
}

class FakeSessionRepository(initial: String? = null) : SessionRepository {
    override val signedInUserId = MutableStateFlow(initial)

    override suspend fun signIn(userId: String) {
        signedInUserId.value = userId
    }

    override suspend fun signOut() {
        signedInUserId.value = null
    }
}

class FakeConnectivityObserver(online: Boolean = true) : ConnectivityObserver {
    override val isOnline = MutableStateFlow(online)
}

object TestData {
    val maxi = User("maxi", "maxi", "Maxi", "avatar_maxi", 25_000)
    val annie = User("annie", "annie", "Annie", "avatar_annie", 18_050)
    val users = listOf(maxi, annie)

    fun tx(id: String, payer: String = "annie", payee: String = "maxi", cents: Long = 3_115, memo: String = "Tony's Split Party") =
        Transaction(id, payer, payee, cents, memo, null, null, createdAtMillis = 0L, likesCount = 0, commentsCount = 0)

    /** A server-confirmed snapshot, as Firestore delivers it when online. */
    fun <T> server(value: T) = LiveEvent.Data(value, fromCache = false)
}
