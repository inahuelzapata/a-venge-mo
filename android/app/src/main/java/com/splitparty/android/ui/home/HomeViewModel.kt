package com.splitparty.android.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.map
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val currentUserName: String,
    val feed: LiveState<ImmutableList<Transaction>> = LiveState.Loading,
    val users: ImmutableMap<String, User> = persistentMapOf(),
    /** Ids of rows that just arrived; each stays here briefly, then its highlight fades. */
    val highlighted: ImmutableSet<String> = persistentSetOf()
)

class HomeViewModel(private val userId: String, observeFeed: ObserveFeedUseCase, observeUsers: ObserveUsersUseCase) : ViewModel() {
    private val highlighted = MutableStateFlow(persistentSetOf<String>())
    private var knownIds: Set<String>? = null

    private val users = observeUsers()
        .map { it.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }

    private val feed = observeFeed()
        .onEach(::markNewRows)
        .map { state -> state.map { it.toImmutableList() } }

    // Eagerly: the listener stays on while another tab is open, so rows that
    // arrive meanwhile are already there (and highlighted) on return.
    val state: StateFlow<HomeUiState> = combine(feed, users, highlighted) { feed, users, highlighted ->
        HomeUiState(
            currentUserName = users[userId]?.displayName ?: Username.fallbackName(userId),
            feed = feed,
            users = users,
            highlighted = highlighted
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(currentUserName = Username.fallbackName(userId)))

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
        const val HIGHLIGHT_HOLD_MS = 900L
    }
}
