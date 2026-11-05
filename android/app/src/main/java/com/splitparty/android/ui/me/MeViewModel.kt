package com.splitparty.android.ui.me

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.map
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.repository.DemoResetFailure
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoResetResult
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.usecase.ObserveUserTransactionsUseCase
import com.splitparty.android.domain.usecase.ObserveUserUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.domain.usecase.SignOutUseCase
import com.splitparty.android.ui.home.MerchantCardEntrance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
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
    val demoAtMerchant: Boolean? = null,
    val reset: DemoResetUi = DemoResetUi.Idle
)

/** The Demo reset button's state. Every phone's data comes back through the live listeners, not from here. */
@Immutable
sealed interface DemoResetUi {
    data object Idle : DemoResetUi

    data object Running : DemoResetUi

    data object Done : DemoResetUi

    /** [message] is the function's own text (e.g. a partial reset), shown as is when present. */
    data class Failed(val reason: DemoResetFailure, val message: String? = null) : DemoResetUi
}

sealed interface MeAction {
    data object LogOut : MeAction

    data class SetDemoAtMerchant(val on: Boolean) : MeAction

    /** Already confirmed: call `resetDemo`. */
    data object ResetDemo : MeAction
}

/** Profile, live mock balance, the demo switch and the user's own payments. Deliberately nothing else (no friends list). */
class MeViewModel(
    private val userId: String,
    observeUser: ObserveUserUseCase,
    observeUserTransactions: ObserveUserTransactionsUseCase,
    observeUsers: ObserveUsersUseCase,
    private val signOut: SignOutUseCase,
    private val demoSettings: DemoSettingsRepository,
    private val demoReset: DemoResetRepository,
    private val merchantEntrance: MerchantCardEntrance = MerchantCardEntrance()
) : ViewModel() {
    private val resetState = MutableStateFlow<DemoResetUi>(DemoResetUi.Idle)

    private val initial = MeUiState(userId = userId, displayName = Username.fallbackName(userId))

    val state: StateFlow<MeUiState> = combine(
        observeUser(userId),
        observeUserTransactions(userId).map { state -> state.map { it.toImmutableList() } },
        observeUsers().map { it.toImmutableMap() }.onStart { emit(persistentMapOf()) },
        demoSettings.atMerchant.map<Boolean, Boolean?> { it }.onStart { emit(null) },
        resetState
    ) { profile, transactions, users, demoAtMerchant, reset ->
        val user = (profile as? LiveState.Ready)?.value ?: users[userId]
        MeUiState(
            userId = userId,
            displayName = user?.displayName ?: initial.displayName,
            avatar = user?.avatar,
            profile = profile,
            transactions = transactions,
            users = users,
            demoAtMerchant = demoAtMerchant,
            reset = reset
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    fun onAction(action: MeAction) {
        when (action) {
            // Signing out flips the session; the app root then shows login.
            MeAction.LogOut -> viewModelScope.launch { signOut() }

            is MeAction.SetDemoAtMerchant -> {
                // Switched off, the card's next arrival plays again, as on iOS.
                if (!action.on) merchantEntrance.replay()
                viewModelScope.launch { demoSettings.setAtMerchant(action.on) }
            }

            MeAction.ResetDemo -> resetDemo()
        }
    }

    /** One call at a time; a failure changes nothing locally and the button can be tapped again. */
    private fun resetDemo() {
        if (resetState.value == DemoResetUi.Running) return
        resetState.value = DemoResetUi.Running
        viewModelScope.launch {
            resetState.value = when (val result = demoReset.reset()) {
                DemoResetResult.Done -> {
                    // The demo starts over: Home plays the merchant card's arrival again.
                    merchantEntrance.replay()
                    DemoResetUi.Done
                }

                is DemoResetResult.Failed -> DemoResetUi.Failed(result.reason, result.message)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
