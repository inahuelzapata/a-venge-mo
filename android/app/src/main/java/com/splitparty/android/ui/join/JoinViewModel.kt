package com.splitparty.android.ui.join

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.party.JoinCodes
import com.splitparty.android.domain.repository.JoinPartyResult
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import kotlin.random.Random
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class JoinPhase { Loading, Choosing, Locked, Unavailable }

@Immutable
data class JoinUiState(
    val phase: JoinPhase = JoinPhase.Loading,
    val hostName: String = "",
    /** Four two-emoji options, exactly one of them the host's code. */
    val options: ImmutableList<ImmutableList<String>> = persistentListOf(),
    /** Index of the option that was just picked wrong: it shakes, then the options reshuffle. */
    val wrongPick: Int? = null,
    /** "That's not the code" stays up after a wrong pick until the next pick. */
    val showWrongCode: Boolean = false,
    /** Bumped on each wrong pick so the shake replays even on the same index. */
    val wrongPickCount: Int = 0,
    val busy: Boolean = false,
    val joinFailed: Boolean = false
)

sealed interface JoinEvent {
    data class Joined(val partyId: String) : JoinEvent

    /** Error haptic. */
    data object WrongCode : JoinEvent
}

/**
 * Code confirmation (PoC-9): the joinee picks the host's code among four. A
 * correct pick joins, a wrong one counts an attempt on the party and
 * reshuffles; three wrong picks lock that party for this user.
 */
class JoinViewModel(
    private val partyId: String,
    private val userId: String,
    private val parties: PartyRepository,
    observeUsers: ObserveUsersUseCase,
    private val clock: Clock,
    private val random: Random = Random.Default
) : ViewModel() {
    private val _events = Channel<JoinEvent>(Channel.BUFFERED)
    val events: Flow<JoinEvent> = _events.receiveAsFlow()

    private val local = MutableStateFlow(Local())
    private var latest: Party? = null

    private val party = parties.party(partyId)
        .filterIsInstance<LiveEvent.Data<Party?>>()
        .onEach { event ->
            latest = event.value
            // First options as soon as the code is known; later ones only after a wrong pick.
            if (local.value.options.isEmpty()) event.value?.let { local.update { l -> l.copy(options = shuffled(it)) } }
        }

    val state: StateFlow<JoinUiState> = combine(party, observeUsers().onStart { emit(emptyMap()) }, local) { event, users, local ->
        val party = event.value
        val hostName = party?.let { users[it.hostId]?.displayName ?: Username.fallbackName(it.hostId) }.orEmpty()
        // Wrong picks made here count at once, before the increment round-trips.
        val attempts = maxOf(party?.wrongAttemptsOf(userId) ?: 0L, local.wrongPicks.toLong())
        val phase = when {
            party == null -> if (event.fromCache) JoinPhase.Loading else JoinPhase.Unavailable

            // The last wrong pick shakes first; the locked state follows the shake.
            attempts >= Party.MAX_WRONG_CODE_ATTEMPTS && !local.lockAfterShake -> JoinPhase.Locked

            local.lockAfterShake -> JoinPhase.Choosing

            !party.isActiveAt(clock.now()) || local.unavailable -> JoinPhase.Unavailable

            else -> JoinPhase.Choosing
        }
        JoinUiState(
            phase = phase,
            hostName = hostName,
            options = local.options,
            wrongPick = local.wrongPick,
            showWrongCode = local.showWrongCode,
            wrongPickCount = local.wrongPicks,
            busy = local.busy,
            joinFailed = local.joinFailed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), JoinUiState())

    fun pick(index: Int) {
        val party = latest
        val current = local.value
        val option = current.options.getOrNull(index)
        val canPick = !current.busy && state.value.phase == JoinPhase.Choosing
        if (party == null || option == null || !canPick) return
        if (option == party.joinCode) {
            local.update { it.copy(busy = true, joinFailed = false, wrongPick = null, showWrongCode = false) }
            viewModelScope.launch {
                when (parties.join(partyId, userId)) {
                    JoinPartyResult.Joined -> _events.send(JoinEvent.Joined(partyId))
                    JoinPartyResult.NotJoinable -> local.update { it.copy(busy = false, unavailable = true) }
                    JoinPartyResult.Failed -> local.update { it.copy(busy = false, joinFailed = true) }
                }
                local.update { it.copy(busy = false) }
            }
        } else {
            _events.trySend(JoinEvent.WrongCode)
            local.update {
                val picks = maxOf(it.wrongPicks, party.wrongAttemptsOf(userId).toInt()) + 1
                it.copy(
                    wrongPicks = picks,
                    wrongPick = index,
                    showWrongCode = true,
                    joinFailed = false,
                    busy = true,
                    lockAfterShake = picks >= Party.MAX_WRONG_CODE_ATTEMPTS
                )
            }
            viewModelScope.launch { parties.recordWrongAttempt(partyId, userId) }
            viewModelScope.launch {
                // Let the picked option shake in place, then deal a new order.
                delay(SHAKE_MS)
                local.update { it.copy(options = shuffled(party), wrongPick = null, busy = false, lockAfterShake = false) }
            }
        }
    }

    private fun shuffled(party: Party): ImmutableList<ImmutableList<String>> =
        JoinCodes.options(party.joinCode, random).map { it.toImmutableList() }.toImmutableList()

    private data class Local(
        val options: ImmutableList<ImmutableList<String>> = persistentListOf(),
        val wrongPicks: Int = 0,
        val wrongPick: Int? = null,
        val busy: Boolean = false,
        val joinFailed: Boolean = false,
        val showWrongCode: Boolean = false,
        val unavailable: Boolean = false,
        /** The third wrong pick is still shaking: Locked shows once it has played. */
        val lockAfterShake: Boolean = false
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Length of the shake (JoinScreen) before the reshuffle. */
        const val SHAKE_MS = 400L
    }
}
