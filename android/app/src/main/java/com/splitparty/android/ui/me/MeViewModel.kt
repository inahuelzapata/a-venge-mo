package com.splitparty.android.ui.me

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.map
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.usecase.ObserveUserTransactionsUseCase
import com.splitparty.android.domain.usecase.ObserveUserUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.domain.usecase.SignOutUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class MeUiState(
    val userId: String,
    val displayName: String,
    val avatar: String? = null,
    /** `users/{userId}`: Ready(null) means the account no longer exists. */
    val profile: LiveState<User?> = LiveState.Loading,
    val transactions: LiveState<ImmutableList<Transaction>> = LiveState.Loading,
    val users: ImmutableMap<String, User> = persistentMapOf(),
    /** "Demo mode: at Tony's"; null until the stored value is read. */
    val demoAtMerchant: Boolean? = null
)

sealed interface MeAction {
    data object LogOut : MeAction

    data class SetDemoAtMerchant(val on: Boolean) : MeAction
}

/** Profile, live mock balance, the demo switch and the user's own payments. Deliberately nothing else (no friends list). */
class MeViewModel(
    private val userId: String,
    observeUser: ObserveUserUseCase,
    observeUserTransactions: ObserveUserTransactionsUseCase,
    observeUsers: ObserveUsersUseCase,
    private val signOut: SignOutUseCase,
    private val demoSettings: DemoSettingsRepository
) : ViewModel() {
    private val initial = MeUiState(userId = userId, displayName = Username.fallbackName(userId))

    val state: StateFlow<MeUiState> = combine(
        observeUser(userId),
        observeUserTransactions(userId).map { state -> state.map { it.toImmutableList() } },
        observeUsers().map { it.toImmutableMap() }.onStart { emit(persistentMapOf()) },
        demoSettings.atMerchant.map<Boolean, Boolean?> { it }.onStart { emit(null) }
    ) { profile, transactions, users, demoAtMerchant ->
        val user = (profile as? LiveState.Ready)?.value ?: users[userId]
        MeUiState(
            userId = userId,
            displayName = user?.displayName ?: initial.displayName,
            avatar = user?.avatar,
            profile = profile,
            transactions = transactions,
            users = users,
            demoAtMerchant = demoAtMerchant
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    fun onAction(action: MeAction) {
        when (action) {
            // Signing out flips the session; the app root then shows login.
            MeAction.LogOut -> viewModelScope.launch { signOut() }

            is MeAction.SetDemoAtMerchant -> viewModelScope.launch { demoSettings.setAtMerchant(action.on) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
