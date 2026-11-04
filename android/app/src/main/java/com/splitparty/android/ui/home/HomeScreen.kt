package com.splitparty.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.splitparty.android.data.LiveEvent
import com.splitparty.android.data.NetworkMonitor
import com.splitparty.android.data.SplitPartyRepository
import com.splitparty.android.data.Transaction
import com.splitparty.android.data.User
import com.splitparty.android.data.Username
import com.splitparty.android.ui.LiveState
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.InitialBadge
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.toLiveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(repository: SplitPartyRepository, network: NetworkMonitor) : ViewModel() {
    val users: StateFlow<Map<String, User>> = repository.users()
        .filterIsInstance<LiveEvent.Data<Map<String, User>>>()
        .map { it.value }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _highlighted = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of rows that just arrived; each stays here briefly, then its highlight fades. */
    val highlighted: StateFlow<Set<String>> = _highlighted.asStateFlow()
    private var knownIds: Set<String>? = null

    // Eagerly: the listener stays on while another tab is open, so rows that
    // arrive meanwhile are already there (and highlighted) on return.
    val feed: StateFlow<LiveState<List<Transaction>>> = repository.feed()
        .toLiveState(network.isOnline)
        .onEach(::markNewRows)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveState.Loading)

    private fun markNewRows(state: LiveState<List<Transaction>>) {
        if (state !is LiveState.Ready) return
        val ids = state.value.mapTo(HashSet()) { it.id }
        val previous = knownIds
        knownIds = ids
        if (previous == null) return // first load: nothing is "new"
        val fresh = ids - previous
        if (fresh.isEmpty()) return
        _highlighted.update { it + fresh }
        viewModelScope.launch {
            delay(HIGHLIGHT_HOLD_MS)
            _highlighted.update { it - fresh }
        }
    }

    private companion object {
        const val HIGHLIGHT_HOLD_MS = 900L
    }
}

@Composable
fun HomeRoute(userId: String, repository: SplitPartyRepository, network: NetworkMonitor, contentPadding: PaddingValues) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer { HomeViewModel(repository, network) }
    })
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val highlighted by viewModel.highlighted.collectAsStateWithLifecycle()
    HomeScreen(
        currentUserName = users[userId]?.displayName ?: Username.fallbackName(userId),
        feed = feed,
        users = users,
        highlighted = highlighted,
        contentPadding = contentPadding,
    )
}

@Composable
fun HomeScreen(
    currentUserName: String,
    feed: LiveState<List<Transaction>>,
    users: Map<String, User>,
    highlighted: Set<String>,
    contentPadding: PaddingValues,
) {
    val now = rememberNow()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = top + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The header is the first item, so while it is visible a new row is
        // inserted below it and stays on screen.
        item(key = "header") { HomeHeader(currentUserName) }
        item(key = "banner-slot") { BannerSlot() }
        when (feed) {
            LiveState.Loading -> item(key = "loading") { LoadingCard() }
            is LiveState.Unreachable -> item(key = "error") { UnreachableCard(feed.message) }
            is LiveState.Ready ->
                if (feed.value.isEmpty()) {
                    item(key = "empty") {
                        MessageCard("🎉", "No payments yet", "When friends pay each other, it shows up here.")
                    }
                } else {
                    items(feed.value, key = { it.id }) { transaction ->
                        FeedRow(
                            transaction = transaction,
                            users = users,
                            nowMillis = now,
                            highlighted = transaction.id in highlighted,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
        }
    }
}

/** Initial badge on the left, search/QR pill on the right (decorative), as in the reference. */
@Composable
private fun HomeHeader(currentUserName: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialBadge(currentUserName)
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(50),
            color = Brand.Card,
            border = BorderStroke(1.dp, Brand.Outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(26.dp))
                Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/**
 * Reserved for the merchant banner (PoC-7) and the Join banner (PoC-8), which
 * later slices place here. Empty in this slice.
 */
@Composable
private fun BannerSlot() {
    Box(Modifier.fillMaxWidth())
}
