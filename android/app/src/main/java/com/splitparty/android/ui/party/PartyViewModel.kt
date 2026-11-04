package com.splitparty.android.ui.party

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.countdown
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One seat in the member row, in join order (host first). */
@Immutable
data class MemberUi(val userId: String, val firstName: String, val avatar: String?, val isHost: Boolean)

enum class PartyPhase { Loading, Open, Expired, Settled, NotFound }

@Immutable
data class PartyUiState(
    val phase: PartyPhase = PartyPhase.Loading,
    val joinCode: ImmutableList<String> = persistentListOf(),
    val members: ImmutableList<MemberUi> = persistentListOf(),
    val millisLeft: Long = Party.TTL_MILLIS,
    /** Offline, or the last snapshot came from the cache: the screen shows "Reconnecting…". */
    val reconnecting: Boolean = false
)

sealed interface PartyEvent {
    /** Someone joined while this user was already in the party: haptic. */
    data object MemberJoined : PartyEvent
}

/**
 * The party screen for host and members alike: the live party document, the
 * countdown by this phone's clock, and expiry. Expired = the clock passed
 * `expiresAt`, whatever the stored status; the host's phone then writes it.
 */
@Suppress("LongParameterList")
class PartyViewModel(
    private val partyId: String,
    private val userId: String,
    private val parties: PartyRepository,
    observeUsers: ObserveUsersUseCase,
    connectivity: ConnectivityObserver,
    private val clock: Clock
) : ViewModel() {
    private val _events = Channel<PartyEvent>(Channel.BUFFERED)
    val events: Flow<PartyEvent> = _events.receiveAsFlow()

    private var knownMembers: List<String>? = null

    /** One listener for everything on this screen. */
    private val lastData = parties.party(partyId)
        .onEach(::noticeJoins)
        .filterIsInstance<LiveEvent.Data<Party?>>()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** This phone's clock, every second until the party's expiry and exactly at it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val now = lastData.map { it.value?.expiresAtMillis }.distinctUntilChanged().flatMapLatest { expiresAt ->
        if (expiresAt == null) flowOf(clock.now()) else clock.countdown(expiresAt)
    }

    private val users = observeUsers().onStart { emit(emptyMap()) }

    val state: StateFlow<PartyUiState> = combine(lastData, now, users, connectivity.isOnline) { event, now, users, online ->
        val party = event.value
        if (party == null) {
            PartyUiState(phase = if (event.fromCache) PartyPhase.Loading else PartyPhase.NotFound, reconnecting = !online)
        } else {
            PartyUiState(
                phase = when {
                    party.status == PartyStatus.Paid -> PartyPhase.Settled
                    !party.isActiveAt(now) -> PartyPhase.Expired
                    else -> PartyPhase.Open
                },
                joinCode = party.joinCode.toImmutableList(),
                members = party.members.map { it.userId.toMemberUi(users[it.userId], party.hostId) }.toImmutableList(),
                millisLeft = party.millisLeftAt(now),
                reconnecting = !online || event.fromCache
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PartyUiState())

    init {
        // The host's phone writes `expired` (and frees the lock) once the clock passes
        // `expiresAt`, retrying every few seconds while the stored status is still
        // `open` (offline at that moment). A lost race is ignored by the repository;
        // the next snapshot (status expired) cancels the retry loop.
        viewModelScope.launch {
            combine(lastData, now) { event, now -> event.value?.takeIf { it.needsExpiryWrite(now) } }
                .distinctUntilChanged()
                .collectLatest { party ->
                    while (party != null) {
                        parties.expire(party.id)
                        delay(EXPIRE_RETRY_MS)
                    }
                }
        }
    }

    private fun Party.needsExpiryWrite(now: Long) = hostId == userId && status == PartyStatus.Open && now >= expiresAtMillis

    private fun noticeJoins(event: LiveEvent<Party?>) {
        val party = (event as? LiveEvent.Data)?.value ?: return
        val ids = party.memberIds
        val before = knownMembers
        knownMembers = ids
        if (before != null && userId in before && (ids - before.toSet()).isNotEmpty()) {
            _events.trySend(PartyEvent.MemberJoined)
        }
    }

    private fun String.toMemberUi(user: User?, hostId: String) = MemberUi(
        userId = this,
        firstName = (user?.displayName ?: Username.fallbackName(this)).split(' ').first(),
        avatar = user?.avatar,
        isHost = this == hostId
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPIRE_RETRY_MS = 5_000L
    }
}
