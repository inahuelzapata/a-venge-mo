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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.repository.DemoResetFailure
import com.splitparty.android.format.Money
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.EmptyCard
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.IosSwitch
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.components.RollingAmount
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.party.ConfirmDialog
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
fun MeScreen(
    state: MeUiState,
    contentPadding: PaddingValues,
    onAction: (MeAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val now = rememberNow()
    val transactions = state.transactions
    // The list spacing is 12; iOS has 16 between the title, card and heading, hence the extra 4s.
    val extra = Dimens.ContentSpacing - Dimens.CardGap

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(MeTestTags.LIST),
        state = listState,
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
        // Last on the tab, so a presenter finds it but a joinee scrolling their payments does not stumble on it.
        item(key = "demo-reset") {
            DemoResetSection(state.reset, onReset = { onAction(MeAction.ResetDemo) }, modifier = Modifier.padding(top = extra))
        }
    }
}

object MeTestTags {
    const val LIST = "me_list"
    const val DEMO_SWITCH = "me_demo_switch"
    const val BALANCE = "me_balance"
    const val DEMO_RESET = "me_demo_reset"
    const val DEMO_RESET_ERROR = "me_demo_reset_error"
    const val DEMO_RESET_DONE = "me_demo_reset_done"
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

/**
 * "Demo" heading, then a card with what the reset does and the "Demo reset"
 * button (PoC-12). It asks first; while `resetDemo` runs the button shows a
 * spinner; a failure reads inline and the button works again.
 */
@Composable
private fun DemoResetSection(reset: DemoResetUi, onReset: () -> Unit, modifier: Modifier = Modifier) {
    var confirming by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
        Text(
            stringResource(R.string.demo_section),
            style = Type.SectionTitle,
            color = Brand.Ink,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp).semantics { heading() }
        )
        Column(
            Modifier
                .padding(horizontal = Dimens.Gutter)
                .fillMaxWidth()
                .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
                .padding(Dimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerSpacing)
        ) {
            Text(stringResource(R.string.demo_reset_hint), style = Type.Body, color = Brand.InkSecondary)
            val running = reset == DemoResetUi.Running
            val runningLabel = stringResource(R.string.demo_reset_running)
            PillButton(
                text = stringResource(R.string.demo_reset_action),
                onClick = { confirming = true },
                enabled = !running,
                busy = running,
                // While busy the button only shows a spinner: give TalkBack the words.
                modifier = Modifier
                    .testTag(MeTestTags.DEMO_RESET)
                    .semantics { if (running) stateDescription = runningLabel }
            )
            when (reset) {
                is DemoResetUi.Failed -> Text(
                    demoResetErrorText(reset),
                    style = Type.Body,
                    color = Brand.Error,
                    modifier = Modifier.testTag(MeTestTags.DEMO_RESET_ERROR).semantics { liveRegion = LiveRegionMode.Assertive }
                )

                DemoResetUi.Done -> Text(
                    stringResource(R.string.demo_reset_done),
                    style = Type.Body,
                    color = Brand.Ink,
                    modifier = Modifier.testTag(MeTestTags.DEMO_RESET_DONE).semantics { liveRegion = LiveRegionMode.Polite }
                )

                DemoResetUi.Idle, DemoResetUi.Running -> Unit
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.demo_reset_confirm_title),
            body = stringResource(R.string.demo_reset_confirm_body),
            confirmLabel = stringResource(R.string.demo_reset_confirm),
            cancelLabel = stringResource(R.string.dialog_cancel),
            onConfirm = {
                confirming = false
                onReset()
            },
            onDismiss = { confirming = false }
        )
    }
}

/** The function's own message when it sent one (a partial reset says to tap again), else a readable fallback. */
@Composable
private fun demoResetErrorText(failed: DemoResetUi.Failed): String = when (failed.reason) {
    DemoResetFailure.Unreachable -> stringResource(R.string.demo_reset_failed_offline)
    DemoResetFailure.NotDeployed -> stringResource(R.string.demo_reset_failed_not_deployed)
    DemoResetFailure.Server -> failed.message ?: stringResource(R.string.demo_reset_failed_server)
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
            if (balanceCents == null) {
                Text("—", style = Type.Balance, color = Brand.Ink)
            } else {
                // Rolls old → new when a Pay or a reset moves it (instant with animations removed).
                val spoken = stringResource(R.string.me_balance_description, Money.spoken(balanceCents))
                RollingAmount(
                    balanceCents,
                    Modifier.testTag(MeTestTags.BALANCE).clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }
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
