package com.splitparty.android.domain.usecase

import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.toLiveState
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.SplitPartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/** Everyone's newest transactions, with offline/unreachable detection. */
class ObserveFeedUseCase(private val repository: SplitPartyRepository, private val connectivity: ConnectivityObserver) {
    operator fun invoke(): Flow<LiveState<List<Transaction>>> = repository.feed().toLiveState(connectivity.isOnline)
}

/** The schema's Me query: [userId] as payer or payee, newest first. */
class ObserveUserTransactionsUseCase(private val repository: SplitPartyRepository, private val connectivity: ConnectivityObserver) {
    operator fun invoke(userId: String): Flow<LiveState<List<Transaction>>> =
        repository.transactionsOf(userId).toLiveState(connectivity.isOnline)
}

/** `users/{userId}`, live: the balance updates as soon as the document changes. */
class ObserveUserUseCase(private val repository: SplitPartyRepository, private val connectivity: ConnectivityObserver) {
    operator fun invoke(userId: String): Flow<LiveState<User?>> = repository.user(userId).toLiveState(connectivity.isOnline)
}

/** Users by id for names and avatars. Only data is passed on; rows fall back to the id until it arrives. */
class ObserveUsersUseCase(private val repository: SplitPartyRepository) {
    operator fun invoke(): Flow<Map<String, User>> = repository.users()
        .filterIsInstance<LiveEvent.Data<Map<String, User>>>()
        .map { it.value }
}
