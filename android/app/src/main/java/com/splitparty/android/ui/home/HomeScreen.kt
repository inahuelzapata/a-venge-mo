package com.splitparty.android.ui.home

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.InitialBadge
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
@Composable
fun HomeRoute(
    userId: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel { parametersOf(userId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, contentPadding = contentPadding, modifier = modifier)
}

@Composable
fun HomeScreen(state: HomeUiState, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.FEED),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = top + 8.dp,
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The header is the first item, so while it is visible a new row is
        // inserted below it and stays on screen.
        item(key = "header") { HomeHeader(state.currentUserName) }
        item(key = "banner-slot") { BannerSlot() }
        when (val feed = state.feed) {
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
                            users = state.users,
                            nowMillis = now,
                            highlighted = transaction.id in state.highlighted,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
        }
    }
}

object HomeTestTags {
    const val FEED = "home_feed"
}

/** Initial badge on the left, search/QR pill on the right (decorative), as in the reference. */
@Composable
private fun HomeHeader(currentUserName: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialBadge(currentUserName)
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(50),
            color = Brand.Card,
            border = BorderStroke(1.dp, Brand.Outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                HeaderIcon(R.drawable.ic_search)
                HeaderIcon(R.drawable.ic_qr_code)
            }
        }
    }
}

/**
 * Reserved for the merchant banner (PoC-7) and the Join banner (PoC-8), which
 * later slices place here. Empty in this slice.
 */
@Composable
private fun BannerSlot(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth())
}

@Composable
private fun HeaderIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, tint = Brand.Ink, modifier = modifier.size(26.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F3F5)
@Composable
private fun HomeScreenLoadedPreview() {
    SplitPartyTheme {
        HomeScreen(
            HomeUiState(
                currentUserName = "Maxi",
                feed = LiveState.Ready(PreviewData.transactions.toImmutableList()),
                users = PreviewData.users,
                highlighted = persistentSetOf(PreviewData.transactions.first().id)
            ),
            contentPadding = PaddingValues()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F3F5)
@Composable
private fun HomeScreenEmptyPreview() {
    SplitPartyTheme {
        HomeScreen(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf())), contentPadding = PaddingValues())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F3F5)
@Composable
private fun HomeScreenOfflinePreview() {
    SplitPartyTheme {
        HomeScreen(HomeUiState("Maxi", feed = LiveState.Unreachable(OFFLINE_MESSAGE)), contentPadding = PaddingValues())
    }
}
