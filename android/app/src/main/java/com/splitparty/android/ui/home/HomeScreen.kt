package com.splitparty.android.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.EmptyCard
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset
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
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.FEED),
        contentPadding = PaddingValues(
            top = topInset() + Dimens.HeaderTop,
            bottom =
                contentPadding.calculateBottomPadding() + Dimens.ListBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        // The header is the first item, so while it is visible a new row is
        // inserted below it and stays on screen.
        item(key = "header") {
            Column {
                HomeHeader(state.currentUserName, state.currentUserAvatar)
                BannerSlot()
                // 16 between header and feed, as on iOS (12 of it is the list spacing).
                Spacer(Modifier.height(Dimens.ContentSpacing - Dimens.CardGap))
            }
        }
        when (val feed = state.feed) {
            LiveState.Loading -> item(key = "loading") { LoadingCard() }

            is LiveState.Unreachable -> item(key = "error") { UnreachableCard(feed.message) }

            is LiveState.Ready ->
                if (feed.value.isEmpty()) {
                    item(key = "empty") {
                        EmptyCard("No payments yet", "When friends pay each other, it shows up here.")
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

/**
 * As in docs/design/ios-captures/home-feed.png: the user's avatar on the left,
 * "Home" centred, and the search/QR capsule (decorative) on the right.
 */
@Composable
private fun HomeHeader(currentUserName: String, currentUserAvatar: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(Dimens.HeaderHeight).padding(horizontal = Dimens.HeaderMargin),
        contentAlignment = Alignment.Center
    ) {
        Text("Home", style = Type.ScreenTitle, color = Brand.Ink)
        // iOS strokes a 2 white ring centred on the 44 circle's edge: 46 outside, 42 of colour inside.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-1).dp)
                .size(Dimens.HeaderAvatar + 2.dp)
                .dropShadow(CircleShape, Shadow(radius = 4.dp, offset = DpOffset(0.dp, 1.dp), color = Brand.Shadow.copy(alpha = 0.08f)))
                .background(Brand.Card, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Avatar(avatarKey = currentUserAvatar, name = currentUserName, size = Dimens.HeaderAvatar - 2.dp)
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(Dimens.HeaderHeight)
                .background(Brand.Card, CircleShape)
                .border(1.dp, Brand.CapsuleOutline, CircleShape)
                .padding(start = 18.dp, end = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIcon(R.drawable.ic_magnifyingglass, 24.dp)
            HeaderIcon(R.drawable.ic_qrcode, 25.dp)
        }
    }
}

/**
 * Reserved for the merchant banner (PoC-7) and the Join banner (PoC-8), which
 * later slices place here. Empty in this slice, so it takes no space.
 */
@Composable
private fun BannerSlot(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth())
}

@Composable
private fun HeaderIcon(@DrawableRes icon: Int, size: Dp, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, tint = Brand.Ink, modifier = modifier.size(size))
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
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

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun HomeScreenEmptyPreview() {
    SplitPartyTheme {
        HomeScreen(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf())), contentPadding = PaddingValues())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun HomeScreenOfflinePreview() {
    SplitPartyTheme {
        HomeScreen(HomeUiState("Maxi", feed = LiveState.Unreachable(OFFLINE_MESSAGE)), contentPadding = PaddingValues())
    }
}
