package com.splitparty.android.ui.party

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.analytics.Analytics
import com.splitparty.android.domain.analytics.AnalyticsEvent
import com.splitparty.android.domain.countdown
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyMember
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.model.PayBlocker
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.repository.CancelPartyResult
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.LastPartyRepository
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.repository.PartyWriteResult
import com.splitparty.android.domain.split.CustomShareProblem
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
data class MemberUi(val userId: String, val firstName: String, val avatar: String?, val isHost: Boolean, val paid: Boolean = false)

/** [Cancelled]: the host ended it; its own screen, never shown as [Expired] or [Settled]. */
enum class PartyPhase { Loading, Open, Expired, Settled, Cancelled, NotFound }

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
    /**
     * This phone's member's share; null if they are no longer in the party, or
     * while the custom amounts do not fit and this member is on the remainder ([sharePending]).
     */
    val shareCents: Long?,
    /** The custom amounts do not fit the total, so remainder shares are not known. */
    val sharePending: Boolean = false
)

/**
 * One member's line in the shares list (docs/firestore-schema.md "Custom shares"),
 * the same on every phone whatever its switch. [shareCents]: what they pay; null
 * before a total exists (custom members then show [customCents]) or for remainder
 * members while the custom amounts do not fit.
 */
@Immutable
data class MemberShareUi(
    val userId: String,
    val firstName: String,
    val isHost: Boolean,
    val isMe: Boolean,
    val customCents: Long?,
    val shareCents: Long?
) {
    val isCustom: Boolean get() = customCents != null
}

/** The custom amounts against the total, when they do not fit (every phone shows why). */
@Immutable
data class SharesProblemUi(val problem: CustomShareProblem, val customCents: Long, val totalCents: Long)

/**
 * The host's Pay button: enabled only when [Ready], otherwise disabled with the reason as its label.
 * [SharesDontFit]: the custom amounts do not fit the total (docs/firestore-schema.md "Custom shares").
 */
enum class PayState { Ready, NeedsTotal, NeedsMembers, SharesDontFit, Expired, Cancelled }

/**
 * A settled party. [amountCents]: collected (host) or paid (member); null when
 * this user was not settled (no longer a member). [animate]: this screen saw the
 * party go from open to paid, so the ticks, confetti and check play (once).
 */
@Immutable
data class SettlementUi(val isHost: Boolean, val amountCents: Long?, val animate: Boolean)

/**
 * Why a bill, tip, custom amount, removal or leave write did not happen; shown inline.
 * [SharesOverTotal] / [SharesUnassigned]: the custom amount was refused because the amounts
 * would add up to more than the total / everyone would have one without reaching it.
 */
enum class WriteError { NotAllowed, Failed, SharesOverTotal, SharesUnassigned }

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
    val writeError: WriteError? = null,
    /** Host only (members never see a Pay button); null on a member's phone. */
    val pay: PayState? = null,
    /** The Pay write is running. */
    val paying: Boolean = false,
    /** Set once the party is paid. */
    val settlement: SettlementUi? = null,
    /** Host only, while the party is open: the Cancel party control is shown. */
    val canCancel: Boolean = false,
    /** The host's cancel write is running. */
    val cancelling: Boolean = false,
    /** Every member's share, host first. Listed on screen once anyone has a custom amount, or while [canEditShares]. */
    val shares: ImmutableList<MemberShareUi> = persistentListOf(),
    /** Host, open party, this phone's "Custom shares" switch on: tapping a share edits it. */
    val canEditShares: Boolean = false,
    /** Set while the custom amounts do not fit the total. */
    val sharesProblem: SharesProblemUi? = null
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

    /** The party this screen showed was deleted (a Demo reset): straight back to Home. */
    data object Gone : PartyEvent

    /** The party this screen showed open just got paid: the settlement plays, with a success haptic. */
    data object Settled : PartyEvent

    /** The host's own cancel landed: back to Home, where the merchant card is free again. */
    data object CancelledByMe : PartyEvent
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
    private val clock: Clock,
    private val analytics: Analytics = Analytics.None,
    private val lastParty: LastPartyRepository = LastPartyRepository.None,
    /** This phone's "Custom shares" switch (Me tab): only shows or hides the host's controls. */
    customSharesSwitch: Flow<Boolean> = flowOf(false)
) : ViewModel() {
    private val _events = Channel<PartyEvent>(Channel.BUFFERED)
    val events: Flow<PartyEvent> = _events.receiveAsFlow()

    private var knownMembers: List<String>? = null
    private var knownShare: Long? = null

    /** Set before this user's own leave write, so the snapshot without them is not read as a removal. */
    private var leaving = false

    /** This screen has seen the party stored `open`, so a later `paid` snapshot is a live settlement. */
    private var sawOpen = false
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

    private val local = combine(writeState, customSharesSwitch.onStart { emit(false) }.distinctUntilChanged()) { write, switchOn ->
        write to switchOn
    }

    val state: StateFlow<PartyUiState> = combine(lastData, now, users, connectivity.isOnline, local) {
            event,
            now,
            users,
            online,
            (write, switchOn)
        ->
        val party = event.value
        if (party == null) {
            PartyUiState(phase = if (event.fromCache) PartyPhase.Loading else PartyPhase.NotFound, reconnecting = !online)
        } else {
            PartyUiState(
                phase = when {
                    party.isCancelled -> PartyPhase.Cancelled
                    party.status == PartyStatus.Paid -> PartyPhase.Settled
                    !party.isActiveAt(now) -> PartyPhase.Expired
                    else -> PartyPhase.Open
                },
                joinCode = party.joinCode.toImmutableList(),
                members = party.members.map { it.toMemberUi(users[it.userId], party.hostId) }.toImmutableList(),
                millisLeft = party.millisLeftAt(now),
                reconnecting = !online || event.fromCache,
                isHost = party.hostId == userId,
                hostName = firstName(party.hostId, users[party.hostId]),
                total = party.toTotalUi(),
                saving = write.saving,
                writeError = write.error,
                pay = party.payState(now),
                paying = write.paying,
                settlement = party.toSettlementUi(),
                canCancel = party.canCancel(userId, now),
                cancelling = write.cancelling,
                shares = party.toSharesUi(users).toImmutableList(),
                canEditShares = switchOn && party.hostId == userId && party.isActiveAt(now),
                sharesProblem = party.toSharesProblemUi()
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

    /**
     * Host: set [memberId]'s custom amount, or null to put them back on the equal split.
     * Amounts that would not fit the total are refused here with the reason, and the
     * party keeps its last valid state; the repository re-checks inside its transaction.
     */
    fun setCustomShare(memberId: String, cents: Long?) {
        val party = latestParty?.takeIf { canWrite() && it.hostId == userId } ?: return
        when {
            // Already that amount: nothing to write, but the keypad still closes.
            party.customShareOf(memberId) == cents -> _events.trySend(PartyEvent.Saved)

            else -> when (party.customShareProblemWith(memberId, cents)) {
                CustomShareProblem.OverTotal -> writeState.value = WriteState(error = WriteError.SharesOverTotal)
                CustomShareProblem.Unassigned -> writeState.value = WriteState(error = WriteError.SharesUnassigned)
                null -> hostWrite { parties.setCustomShare(partyId, userId, memberId, cents) }
            }
        }
    }

    /** Host: remove a non-host member (after the confirmation). */
    fun removeMember(memberId: String) = hostWrite(signalSaved = false) { parties.removeMember(partyId, userId, memberId) }

    /** Non-host member: leave the party (after the confirmation). */
    fun leave() {
        if (!canWrite() || latestParty?.hostId == userId) return
        leaving = true
        launchWrite(signalSaved = false, onDone = { _events.trySend(PartyEvent.Left) }) { parties.leave(partyId, userId) }
    }

    /**
     * Host: settle everyone (no second confirmation: joining was consent). A
     * doubled tap is ignored while the write runs, and the repository treats
     * an already-paid party as done, so a retry never charges twice.
     */
    fun pay() {
        val party = latestParty ?: return
        if (writeState.value.saving || party.hostId != userId || party.payBlockerAt(clock.now()) != null) return
        writeState.value = WriteState(saving = true, paying = true)
        viewModelScope.launch {
            val result = parties.settle(partyId, userId)
            writeState.value = WriteState(
                error = when (result) {
                    PartyWriteResult.Done -> null
                    PartyWriteResult.NotAllowed -> WriteError.NotAllowed
                    PartyWriteResult.Failed -> WriteError.Failed
                }
            )
        }
    }

    /**
     * Host, after "Cancel party?" was confirmed: ends the party for everyone with
     * nothing charged. Records `party_cancelled` once, for the cancel this call
     * committed; on failure the party is still open and the error shows inline.
     */
    fun cancel() {
        val party = latestParty ?: return
        if (writeState.value.saving || !party.canCancel(userId, clock.now())) return
        writeState.value = WriteState(saving = true, cancelling = true)
        viewModelScope.launch {
            val result = parties.cancel(partyId, userId)
            writeState.value = WriteState(
                error = when (result) {
                    is CancelPartyResult.Cancelled -> null
                    CancelPartyResult.NotAllowed -> WriteError.NotAllowed
                    CancelPartyResult.Failed -> WriteError.Failed
                }
            )
            if (result is CancelPartyResult.Cancelled) {
                result.cancellation?.let { analytics.record(AnalyticsEvent.partyCancelled(it)) }
                lastParty.forget()
                _events.trySend(PartyEvent.CancelledByMe)
            }
        }
    }

    /** "Back to Home" from a finished party: a relaunch no longer reopens it. */
    fun leaveFinishedParty() {
        viewModelScope.launch { lastParty.forget() }
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
            val pending = !split.fits && customShareOf(userId) == null
            TotalUi(
                billCents = bill,
                tip = TipChoice.of(tip),
                tipCents = split.tipCents,
                totalCents = split.totalCents,
                headcount = split.headcount,
                shareCents = shareOf(userId).takeUnless { pending },
                sharePending = pending && hasMember(userId)
            )
        }
    }

    /** Everyone's share by the extended split rule (the screen decides when to list them). */
    private fun Party.toSharesUi(users: Map<String, User>): List<MemberShareUi> {
        val split = split
        return members.mapIndexed { i, member ->
            MemberShareUi(
                userId = member.userId,
                firstName = firstName(member.userId, users[member.userId]),
                isHost = member.userId == hostId,
                isMe = member.userId == userId,
                customCents = member.customShareCents,
                shareCents = split?.takeIf { it.fits || member.customShareCents != null }?.shares?.getOrNull(i)
            )
        }
    }

    private fun Party.toSharesProblemUi(): SharesProblemUi? {
        val split = split ?: return null
        return split.problem?.let { SharesProblemUi(it, split.customCents, split.totalCents) }
    }

    private data class WriteState(
        val saving: Boolean = false,
        val error: WriteError? = null,
        val paying: Boolean = false,
        val cancelling: Boolean = false
    )

    private fun Party.payState(now: Long): PayState? = if (hostId != userId) {
        null
    } else {
        when (payBlockerAt(now)) {
            null, PayBlocker.AlreadyPaid -> PayState.Ready
            PayBlocker.Expired -> PayState.Expired
            PayBlocker.Cancelled -> PayState.Cancelled
            PayBlocker.NoTotal -> PayState.NeedsTotal
            PayBlocker.NoMembers -> PayState.NeedsMembers
            PayBlocker.CustomSharesDontFit -> PayState.SharesDontFit
        }
    }

    private fun Party.toSettlementUi(): SettlementUi? {
        if (status != PartyStatus.Paid) return null
        val shares = split?.shares
        val amount = when {
            shares == null || !hasMember(userId) -> null
            hostId == userId -> joinees.sumOf { shares[memberIds.indexOf(it.userId)] }
            else -> shareOf(userId)
        }
        return SettlementUi(isHost = hostId == userId, amountCents = amount, animate = sawOpen)
    }

    init {
        // Remembered so a relaunch can show how this party ended while the app was closed.
        viewModelScope.launch { lastParty.remember(partyId) }
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
        val data = event as? LiveEvent.Data ?: return
        val party = data.value
        if (party == null) {
            noticeGone(data.fromCache)
            return
        }
        noticeSettlement(party)
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

    /** Only the server can say a party this screen already showed no longer exists (a Demo reset deleted it). */
    private fun noticeGone(fromCache: Boolean) {
        if (latestParty != null && !fromCache) _events.trySend(PartyEvent.Gone)
    }

    /** Open → paid while this screen watched: the settlement plays. Opened already paid: it does not. */
    private fun noticeSettlement(party: Party) {
        if (party.status == PartyStatus.Open) {
            sawOpen = true
        } else if (party.status == PartyStatus.Paid && sawOpen && latestParty?.status == PartyStatus.Open) {
            _events.trySend(PartyEvent.Settled)
        }
    }

    private fun firstName(id: String, user: User?): String = (user?.displayName ?: Username.fallbackName(id)).split(' ').first()

    private fun PartyMember.toMemberUi(user: User?, hostId: String) = MemberUi(
        userId = userId,
        firstName = firstName(userId, user),
        avatar = user?.avatar,
        isHost = userId == hostId,
        paid = paid
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPIRE_RETRY_MS = 5_000L
    }
}
