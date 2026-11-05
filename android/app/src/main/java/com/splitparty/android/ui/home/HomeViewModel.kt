package com.splitparty.android.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.at
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.map
import com.splitparty.android.domain.model.DemoMerchant
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.party.BannerSlot
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.CreatePartyResult
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.usecase.ObserveFeedUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val currentUserName: String,
    /** `users.avatar` of the signed-in user; null (grey initials) until the users load. */
    val currentUserAvatar: String? = null,
    val feed: LiveState<ImmutableList<Transaction>> = LiveState.Loading,
    val users: ImmutableMap<String, User> = persistentMapOf(),
    /** Ids of rows that just arrived; each stays here briefly, then its highlight fades. */
    val highlighted: ImmutableSet<String> = persistentSetOf(),
    /** Merchant card, Join banner, "Back to your party" or nothing. */
    val bannerSlot: BannerSlot = BannerSlot.Merchant,
    /** "Start a party" is creating the party. */
    val startingParty: Boolean = false,
    /** Inline error under the merchant card after a failed "Start a party". */
    val startPartyFailed: Boolean = false
)

/** One-shot navigation requests from Home. */
sealed interface HomeEvent {
    data class OpenParty(val partyId: String) : HomeEvent
}

@Suppress("LongParameterList")
class HomeViewModel(
    private val userId: String,
    observeFeed: ObserveFeedUseCase,
    observeUsers: ObserveUsersUseCase,
    private val parties: PartyRepository,
    demoSettings: DemoSettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val merchantEntrance: MerchantCardEntrance = MerchantCardEntrance()
) : ViewModel() {
    private val highlighted = MutableStateFlow(persistentSetOf<String>())
    private var knownIds: Set<String>? = null
    private val startState = MutableStateFlow(StartState())
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)

    /** Navigation requests: open the party screen. */
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private val users = observeUsers()
        .map { it.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }

    /** Open parties at Tony's; any snapshot (cached too) so a dropped connection keeps the last known slot. */
    private val openParties = parties.openParties(DemoMerchant.ID)
        .filterIsInstance<LiveEvent.Data<List<Party>>>()
        .onEach(::resumeOnLaunch)
        .map { it.value }
        .onStart { emit(emptyList()) }

    private var latestSlot: BannerSlot = BannerSlot.None
    private var resumeChecked = false

    /** Re-read the clock at every party's expiry, so the slot changes the moment one passes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val partiesNow = openParties.flatMapLatest { list ->
        clock.at(list.map { it.expiresAtMillis }).map { now -> list to now }
    }

    private val bannerSlot = combine(partiesNow, users, demoSettings.atMerchant) { (list, now), users, demo ->
        BannerSlot.resolve(userId, users[userId]?.friendIds.orEmpty(), list, demo, now)
    }.distinctUntilChanged().onEach { latestSlot = it }

    private val feed = observeFeed()
        .onEach(::markNewRows)
        .map { state -> state.map { it.toImmutableList() } }

    // Eagerly: the listener stays on while another tab is open, so rows that
    // arrive meanwhile are already there (and highlighted) on return.
    val state: StateFlow<HomeUiState> = combine(feed, users, highlighted, bannerSlot, startState) { feed, users, highlighted, slot, start ->
        HomeUiState(
            currentUserName = users[userId]?.displayName ?: Username.fallbackName(userId),
            currentUserAvatar = users[userId]?.avatar,
            feed = feed,
            users = users,
            highlighted = highlighted,
            bannerSlot = slot,
            startingParty = start.busy,
            startPartyFailed = start.failed && slot == BannerSlot.Merchant
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        HomeUiState(currentUserName = Username.fallbackName(userId), bannerSlot = BannerSlot.None)
    )

    init {
        // A party this user hosted and left open past its expiry (app closed at the time) is closed now.
        viewModelScope.launch { parties.expireStaleHostedParties(userId, clock.now()) }
    }

    /**
     * "Start a party": returns to the user's open party if there is one,
     * otherwise creates one (one transaction; nothing is left behind on failure).
     */
    fun startParty() {
        if (startState.value.busy) return
        (latestSlot as? BannerSlot.BackToParty)?.let {
            _events.trySend(HomeEvent.OpenParty(it.party.id))
            return
        }
        startState.value = StartState(busy = true)
        viewModelScope.launch {
            val result = if (connectivity.isOnline.first()) {
                parties.createParty(userId, DemoMerchant.ID, clock.now())
            } else {
                CreatePartyResult.Failed
            }
            when (result) {
                is CreatePartyResult.Created -> {
                    startState.value = StartState()
                    _events.send(HomeEvent.OpenParty(result.partyId))
                }

                CreatePartyResult.Failed -> startState.value = StartState(failed = true)
            }
        }
    }

    /**
     * Relaunch: the first server-confirmed snapshot that shows this user in an
     * active party opens it straight away (once per Home).
     */
    private fun resumeOnLaunch(event: LiveEvent.Data<List<Party>>) {
        if (resumeChecked || event.fromCache) return
        resumeChecked = true
        val now = clock.now()
        event.value.filter { it.isActiveAt(now) && it.hasMember(userId) }.maxByOrNull { it.createdAtMillis }?.let {
            _events.trySend(HomeEvent.OpenParty(it.id))
        }
    }

    private data class StartState(val busy: Boolean = false, val failed: Boolean = false)

    /** True once per arrival of the merchant card: Home plays its slide and sheen. */
    fun takeMerchantEntrance(): Boolean = merchantEntrance.take()

    /** Highlights ids that were not in the previous server snapshot. The first load highlights nothing. */
    private fun markNewRows(state: LiveState<List<Transaction>>) {
        if (state !is LiveState.Ready) return
        val ids = state.value.mapTo(HashSet()) { it.id }
        val fresh = knownIds?.let { ids - it }.orEmpty()
        knownIds = ids
        if (fresh.isEmpty()) return
        highlighted.update { it.addAll(fresh) }
        viewModelScope.launch {
            delay(HIGHLIGHT_HOLD_MS)
            highlighted.update { (it - fresh).toPersistentSet() }
        }
    }

    companion object {
        /** As on iOS: a new row stays tinted for 1.5 s, then fades back to white. */
        const val HIGHLIGHT_HOLD_MS = 1_500L
    }
}
