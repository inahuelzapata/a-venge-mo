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
import com.splitparty.android.domain.repository.PartyWriteResult
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The tip as the chips show it. A stored null or custom 0 is the 0% chip. */
@Immutable
sealed interface TipChoice {
    data object Zero : TipChoice

    data class Percent(val percent: Int) : TipChoice

    data class Custom(val amountCents: Long) : TipChoice

    companion object {
        /** The host's chips, in order: 0%, 15%, 18%, 20%, then Custom. */
        val PRESET_PERCENTS = listOf(15, 18, 20)

        fun of(tip: Tip?): TipChoice = when (tip) {
            null -> Zero
            is Tip.Percent -> Percent(tip.percent)
            is Tip.Custom -> if (tip.amountCents == 0L) Zero else Custom(tip.amountCents)
        }
    }
}

/** Everything the total area shows once a bill exists, all from one snapshot so they never disagree. */
@Immutable
data class TotalUi(
    val billCents: Long,
    val tip: TipChoice,
    val tipCents: Long,
    val totalCents: Long,
    val headcount: Int,
    /** This phone's member's share; null if they are no longer in the party. */
    val shareCents: Long?
)

/** Why a bill, tip, removal or leave write did not happen; shown inline. */
enum class WriteError { NotAllowed, Failed }

@Immutable
data class PartyUiState(
    val phase: PartyPhase = PartyPhase.Loading,
    val joinCode: ImmutableList<String> = persistentListOf(),
    val members: ImmutableList<MemberUi> = persistentListOf(),
    val millisLeft: Long = Party.TTL_MILLIS,
    /** Offline, or the last snapshot came from the cache: the screen shows "Reconnecting…". */
    val reconnecting: Boolean = false,
    /** This phone's user hosts the party: entry, tip chips and removal are theirs alone. */
    val isHost: Boolean = false,
    /** First name of the host, for "Annie's party". */
    val hostName: String = "",
    /** Null until the host confirms a bill: "Waiting for the total" / "Add the total". */
    val total: TotalUi? = null,
    /** A bill, tip, removal or leave write is running. */
    val saving: Boolean = false,
    val writeError: WriteError? = null
)

sealed interface PartyEvent {
    /** Someone joined while this user was already in the party: haptic. */
    data object MemberJoined : PartyEvent

    /** This member's share changed (total, tip or headcount): light haptic, kept with animations off. */
    data object ShareChanged : PartyEvent

    /** A bill or tip write landed: the entry screens close. */
    data object Saved : PartyEvent

    /** The host removed this user: back to Home with "You were removed from [hostName]'s party". */
    data class Removed(val hostName: String) : PartyEvent

    /** This user left: back to Home. */
    data object Left : PartyEvent
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
    private var knownShare: Long? = null

    /** Set before this user's own leave write, so the snapshot without them is not read as a removal. */
    private var leaving = false
    private val writeState = MutableStateFlow(WriteState())
    private var latestParty: Party? = null

    /** One listener for everything on this screen. */
    private val lastData = parties.party(partyId)
        .onEach(::noticeChanges)
        .filterIsInstance<LiveEvent.Data<Party?>>()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** This phone's clock, every second until the party's expiry and exactly at it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val now = lastData.map { it.value?.expiresAtMillis }.distinctUntilChanged().flatMapLatest { expiresAt ->
        if (expiresAt == null) flowOf(clock.now()) else clock.countdown(expiresAt)
    }

    private val users = observeUsers().onStart { emit(emptyMap()) }

    val state: StateFlow<PartyUiState> = combine(lastData, now, users, connectivity.isOnline, writeState) {
            event,
            now,
            users,
            online,
            write
        ->
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
                reconnecting = !online || event.fromCache,
                isHost = party.hostId == userId,
                hostName = firstName(party.hostId, users[party.hostId]),
                total = party.toTotalUi(),
                saving = write.saving,
                writeError = write.error
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PartyUiState())

    /** Host: confirm a bill (cents already validated). The 0% tip goes with it while none is set. */
    fun confirmBill(billCents: Long) = hostWrite { parties.setBill(partyId, userId, billCents) }

    /** Host: pick a tip chip or a custom amount. */
    fun selectTip(choice: TipChoice) {
        val tip = when (choice) {
            TipChoice.Zero -> Tip.ZERO
            is TipChoice.Percent -> Tip.Percent(choice.percent)
            is TipChoice.Custom -> Tip.Custom(choice.amountCents)
        }
        if (latestParty?.tip == tip || (choice == TipChoice.Zero && latestParty?.tip == null)) {
            // Already that tip: nothing to write, but the custom-tip keypad still closes.
            _events.trySend(PartyEvent.Saved)
            return
        }
        hostWrite { parties.setTip(partyId, userId, tip) }
    }

    /** Host: remove a non-host member (after the confirmation). */
    fun removeMember(memberId: String) = hostWrite(signalSaved = false) { parties.removeMember(partyId, userId, memberId) }

    /** Non-host member: leave the party (after the confirmation). */
    fun leave() {
        if (!canWrite() || latestParty?.hostId == userId) return
        leaving = true
        launchWrite(signalSaved = false, onDone = { _events.trySend(PartyEvent.Left) }) { parties.leave(partyId, userId) }
    }

    fun clearWriteError() {
        writeState.value = writeState.value.copy(error = null)
    }

    private fun canWrite(): Boolean = !writeState.value.saving && latestParty?.isActiveAt(clock.now()) == true

    private fun hostWrite(signalSaved: Boolean = true, block: suspend () -> PartyWriteResult) {
        if (!canWrite() || latestParty?.hostId != userId) return
        launchWrite(signalSaved, onDone = {}, block = block)
    }

    private fun launchWrite(signalSaved: Boolean, onDone: () -> Unit, block: suspend () -> PartyWriteResult) {
        writeState.value = WriteState(saving = true)
        viewModelScope.launch {
            val result = block()
            writeState.value = WriteState(
                error = when (result) {
                    PartyWriteResult.Done -> null
                    PartyWriteResult.NotAllowed -> WriteError.NotAllowed
                    PartyWriteResult.Failed -> WriteError.Failed
                }
            )
            if (result == PartyWriteResult.Done) {
                if (signalSaved) _events.trySend(PartyEvent.Saved)
                onDone()
            } else {
                leaving = false
            }
        }
    }

    private fun Party.toTotalUi(): TotalUi? {
        val bill = billCents
        val split = split
        return if (bill == null || split == null) {
            null
        } else {
            TotalUi(
                billCents = bill,
                tip = TipChoice.of(tip),
                tipCents = split.tipCents,
                totalCents = split.totalCents,
                headcount = split.headcount,
                shareCents = shareOf(userId)
            )
        }
    }

    private data class WriteState(val saving: Boolean = false, val error: WriteError? = null)

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

    /** Joins (haptic), this member's share moving (haptic) and this user dropping out of the list (removed). */
    private fun noticeChanges(event: LiveEvent<Party?>) {
        val party = (event as? LiveEvent.Data)?.value ?: return
        val ids = party.memberIds
        val before = knownMembers
        knownMembers = ids
        latestParty = party
        if (before != null && userId in before && (ids - before.toSet()).isNotEmpty()) {
            _events.trySend(PartyEvent.MemberJoined)
        }
        val wasMember = before != null && userId in before
        if (wasMember && userId !in ids && !leaving) {
            val hostName = state.value.hostName.ifEmpty { firstName(party.hostId, null) }
            _events.trySend(PartyEvent.Removed(hostName))
        }
        val share = party.shareOf(userId)
        if (knownShare != null && share != null && share != knownShare) _events.trySend(PartyEvent.ShareChanged)
        knownShare = share
    }

    private fun firstName(id: String, user: User?): String = (user?.displayName ?: Username.fallbackName(id)).split(' ').first()

    private fun String.toMemberUi(user: User?, hostId: String) = MemberUi(
        userId = this,
        firstName = firstName(this, user),
        avatar = user?.avatar,
        isHost = this == hostId
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPIRE_RETRY_MS = 5_000L
    }
}
