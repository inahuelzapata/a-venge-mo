package com.splitparty.android.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.format.Money
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.EmptyCard
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.IosSwitch
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MeRoute(
    userId: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = koinViewModel { parametersOf(userId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MeScreen(state = state, contentPadding = contentPadding, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * As in docs/design/ios-captures/me.png: "Me" title, the profile card (avatar,
 * name, @username, live mock balance, Log out), then "Your payments". Deliberately
 * nothing else in this slice (no friends list).
 */
@Composable
fun MeScreen(state: MeUiState, contentPadding: PaddingValues, onAction: (MeAction) -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val transactions = state.transactions
    // The list spacing is 12; iOS has 16 between the title, card and heading, hence the extra 4s.
    val extra = Dimens.ContentSpacing - Dimens.CardGap

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(MeTestTags.LIST),
        contentPadding = PaddingValues(top = topInset(), bottom = contentPadding.calculateBottomPadding() + Dimens.ListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        item(key = "title") {
            Box(
                Modifier.fillMaxWidth().height(Dimens.HeaderHeight),
                contentAlignment = Alignment.Center
            ) {
                Text("Me", style = Type.ScreenTitle, color = Brand.Ink, modifier = Modifier.padding(top = Dimens.HeaderTop))
            }
            Spacer(Modifier.height(extra))
        }
        item(key = "profile") {
            val balance = (state.profile as? LiveState.Ready)?.value?.balanceCents
            ProfileCard(state.displayName, state.userId, state.avatar, balance, onLogOut = { onAction(MeAction.LogOut) })
            Spacer(Modifier.height(extra))
        }
        item(key = "demo") {
            DemoSwitchCard(
                on = state.demoAtMerchant ?: true,
                onChange = { onAction(MeAction.SetDemoAtMerchant(it)) }
            )
            Spacer(Modifier.height(extra))
        }
        val profile = state.profile
        if (profile is LiveState.Ready && profile.value == null) {
            item(key = "account-missing") {
                MessageCard(
                    icon = R.drawable.ic_exclamationmark_circle_fill,
                    iconWidth = 36.dp,
                    iconHeight = 36.dp,
                    title = "Account not found",
                    message = "users/${state.userId} no longer exists. Log out and sign in again."
                )
            }
        }
        item(key = "payments-title") {
            Text(
                "Your payments",
                style = Type.SectionTitle,
                color = Brand.Ink,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = extra)
            )
        }
        when (transactions) {
            LiveState.Loading -> item(key = "loading") { LoadingCard() }

            is LiveState.Unreachable -> item(key = "error") { UnreachableCard(transactions.message) }

            is LiveState.Ready ->
                if (transactions.value.isEmpty()) {
                    item(key = "empty") {
                        EmptyCard("No payments yet", "Payments you send or receive show up here.")
                    }
                } else {
                    items(transactions.value, key = { it.id }) { transaction ->
                        FeedRow(transaction, state.users, now, highlighted = false, modifier = Modifier.animateItem())
                    }
                }
        }
    }
}

object MeTestTags {
    const val LIST = "me_list"
    const val DEMO_SWITCH = "me_demo_switch"
}

/**
 * The demo stand-in for the geofence (PoC-7): on, Home shows Tony's card as if
 * the phone were at the venue. A white card row with an iOS-style switch.
 */
@Composable
private fun DemoSwitchCard(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .toggleable(
                value = on,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(horizontal = Dimens.CardPadding, vertical = 16.dp)
            .testTag(MeTestTags.DEMO_SWITCH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.demo_at_merchant), style = Type.Amount, color = Brand.Ink)
            Text(stringResource(R.string.demo_at_merchant_hint), style = Type.Body, color = Brand.InkSecondary)
        }
        Spacer(Modifier.width(12.dp))
        IosSwitch(on = on)
    }
}

/** Placeholder dash until the user document has loaded (or while it can't be read), as on iOS. */
@Composable
private fun ProfileCard(
    name: String,
    userId: String,
    avatar: String?,
    balanceCents: Long?,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Avatar(avatar, name, size = 80.dp)
        Text(name, style = Type.ProfileName, color = Brand.Ink)
        Text("@$userId", style = Type.Body, color = Brand.InkSecondary)
        Column(
            // iOS: 12 above and below. Inter's 36 line renders ~1.7 taller than SF's, taken off below.
            modifier = Modifier.padding(top = 12.dp, bottom = 10.3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Balance", style = Type.Body, color = Brand.InkSecondary)
            Text(balanceCents?.let(Money::format) ?: "—", style = Type.Balance, color = Brand.Ink)
        }
        PillButton(text = "Log out", onClick = onLogOut)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun MeScreenPreview() {
    SplitPartyTheme {
        MeScreen(
            MeUiState(
                userId = "maxi",
                displayName = "Maxi",
                avatar = "avatar_maxi",
                profile = LiveState.Ready(PreviewData.maxi),
                transactions = LiveState.Ready(PreviewData.transactions),
                users = PreviewData.users
            ),
            contentPadding = PaddingValues(),
            onAction = {}
        )
    }
}
