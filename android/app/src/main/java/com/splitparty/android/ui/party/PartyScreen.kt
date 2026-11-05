package com.splitparty.android.ui.party

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.domain.model.DemoMerchant
import com.splitparty.android.domain.party.JoinCodes
import com.splitparty.android.ui.components.ActivitySpinner
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.PulsingDot
import com.splitparty.android.ui.components.SoftPillButton
import com.splitparty.android.ui.components.rememberBreathing
import com.splitparty.android.ui.scan.ReceiptScanRoute
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Seats shown until the party is bigger: host plus two friends in the demo. */
private const val MIN_SEATS = 3

@Composable
fun PartyRoute(
    partyId: String,
    userId: String,
    contentPadding: PaddingValues,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoval: (hostName: String) -> Unit = { onHome() },
    viewModel: PartyViewModel = koinViewModel(key = "party_$partyId") { parametersOf(partyId, userId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    var step by rememberTotalStep()
    val currentOnHome by rememberUpdatedState(onHome)
    val currentOnRemoval by rememberUpdatedState(onRemoval)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PartyEvent.MemberJoined -> view.partyHaptic(success = true)

                PartyEvent.ShareChanged -> view.shareTick()

                PartyEvent.Saved -> {
                    view.partyHaptic(success = true)
                    step = null
                }

                is PartyEvent.Removed -> currentOnRemoval(event.hostName)

                PartyEvent.Left, PartyEvent.Gone -> currentOnHome()

                // The settlement view plays its own tick and success haptics; any open entry screen closes.
                PartyEvent.Settled -> step = null
            }
        }
    }
    // Entry screens exist only while the party is open and this phone hosts it.
    val current = step?.takeIf { state.phase == PartyPhase.Open && state.isHost }
    val actions = remember(viewModel) {
        object : TotalActions {
            override fun scan() = open(TotalStep.Scan)

            override fun enterTotal() = open(TotalStep.EnterBill(prefillCents = viewModel.state.value.total?.billCents))

            override fun selectTip(choice: TipChoice) = viewModel.selectTip(choice)

            override fun customTip() = open(TotalStep.EnterTip((viewModel.state.value.total?.tip as? TipChoice.Custom)?.amountCents))

            override fun removeMember(userId: String) = viewModel.removeMember(userId)

            override fun leave() = viewModel.leave()

            override fun pay() = viewModel.pay()

            private fun open(next: TotalStep) {
                viewModel.clearWriteError()
                step = next
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        PartyScreen(state, contentPadding, onBack = onHome, actions = actions)
        if (current != null) {
            TotalEntryOverlay(
                step = current,
                state = state,
                contentPadding = contentPadding,
                onStep = {
                    viewModel.clearWriteError()
                    step = it
                },
                onConfirmBill = viewModel::confirmBill,
                onConfirmTip = { viewModel.selectTip(TipChoice.Custom(it)) },
                scanScreen = { close -> ReceiptScanRoute(onBack = close, onResult = { step = it }) }
            )
        }
    }
}

/**
 * The Split Party screen (PoC-8/9), host and members alike: code, countdown,
 * live member row, and the area the total/tip/share slice fills in later.
 * Provisional layout (docs/design/visual-conventions.md, "Party screen").
 */
@Composable
fun PartyScreen(
    state: PartyUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TotalActions = TotalActions.None
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset() + Dimens.HeaderTop, bottom = contentPadding.calculateBottomPadding())
    ) {
        PartyHeader(stringResource(R.string.party_title, DemoMerchant.NAME), onBack)
        when (state.phase) {
            PartyPhase.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { ActivitySpinner() }

            PartyPhase.Open -> OpenParty(state, actions, Modifier.weight(1f))

            PartyPhase.Expired -> PartyOver(
                stringResource(R.string.party_expired_title),
                stringResource(R.string.party_expired_body),
                onBack,
                // A total set before expiry stays visible, frozen, with no controls.
                frozen = state.total?.let { { TotalArea(state, TotalActions.None, frozen = true) } }
            )

            PartyPhase.Settled -> SettledParty(state, onBack, Modifier.weight(1f))

            PartyPhase.NotFound -> PartyOver(stringResource(R.string.party_not_found), "", onBack)
        }
    }
}

@Composable
private fun OpenParty(state: PartyUiState, actions: TotalActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = Dimens.ContentSpacing, bottom = Dimens.ListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        if (state.reconnecting) ReconnectingPill(Modifier.align(Alignment.CenterHorizontally))
        CodeCard(state.joinCode, state.millisLeft)
        var removing by remember { mutableStateOf<MemberUi?>(null) }
        var leaving by remember { mutableStateOf(false) }
        MembersCard(state.members, onRemove = if (state.isHost) ({ removing = it }) else null)
        TotalArea(state, actions)
        if (!state.isHost) {
            SoftPillButton(
                stringResource(R.string.leave_action),
                onClick = { leaving = true },
                enabled = !state.saving,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag(PartyTestTags.LEAVE)
            )
        }
        removing?.let { member ->
            ConfirmDialog(
                title = stringResource(R.string.remove_title, member.firstName),
                body = stringResource(R.string.remove_body, member.firstName),
                confirmLabel = stringResource(R.string.remove_confirm),
                cancelLabel = stringResource(R.string.dialog_cancel),
                onConfirm = {
                    actions.removeMember(member.userId)
                    removing = null
                },
                onDismiss = { removing = null }
            )
        }
        if (leaving) {
            ConfirmDialog(
                title = stringResource(R.string.leave_title, state.hostName),
                body = stringResource(R.string.leave_body),
                confirmLabel = stringResource(R.string.leave_confirm),
                cancelLabel = stringResource(R.string.leave_cancel),
                onConfirm = actions::leave,
                onDismiss = { leaving = false },
                busy = state.saving,
                error = state.writeError
            )
        }
    }
}

@Composable
private fun PartyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerSpacing)
    ) { content() }
}

@Composable
private fun CodeCard(code: List<String>, millisLeft: Long, modifier: Modifier = Modifier) {
    PartyCard(modifier) {
        Text(stringResource(R.string.party_code_label), style = Type.Body, color = Brand.InkSecondary)
        val spoken = stringResource(R.string.party_code_description, JoinCodes.spokenName(code))
        EmojiCodeTiles(code, Modifier.testTag(PartyTestTags.CODE).clearAndSetSemantics { contentDescription = spoken })
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(size = 8.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.party_expires_in, formatCountdown(millisLeft)),
                style = Type.Amount,
                color = Brand.Ink,
                modifier = Modifier.testTag(PartyTestTags.COUNTDOWN)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MembersCard(members: List<MemberUi>, modifier: Modifier = Modifier, onRemove: ((MemberUi) -> Unit)? = null) {
    // Members present when the screen opened are simply there; later ones spring in.
    val initial = remember { members.map { it.userId }.toSet() }
    // Members who leave or are removed spring out (the arrival reversed) before their seat empties.
    val roster = remember { SeatRoster() }
    var goneCount by remember { mutableIntStateOf(0) }
    val seats = remember(members, goneCount) { roster.merge(members) }
    PartyCard(modifier) {
        Text(
            stringResource(R.string.party_members),
            style = Type.SectionTitle,
            color = Brand.Ink,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(PartyTestTags.MEMBERS).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = 4
        ) {
            seats.forEach { seat ->
                val member = seat.member
                key(member.userId) {
                    val remove = onRemove?.takeIf { !member.isHost && !seat.leaving }?.let { { it(member) } }
                    MemberSeat(
                        member,
                        springIn = member.userId !in initial,
                        leaving = seat.leaving,
                        onDepart = {
                            roster.markGone(member.userId)
                            goneCount++
                        },
                        onRemove = remove
                    )
                }
            }
            // A departed seat turns back into a pulsing empty one once it has sprung out.
            repeat((MIN_SEATS - seats.size).coerceAtLeast(0)) { EmptySeat() }
        }
    }
}

private const val SEAT_FROM_SCALE = 0.3f
private const val EMPTY_SEAT_SHRINK = 0.06f
private const val EMPTY_SEAT_FADE = 0.55f
private const val SEAT_SPRING_DAMPING = 0.7f

/** SwiftUI spring(response: 0.45) as a Compose stiffness: (2π / 0.45)². */
private const val SEAT_SPRING_STIFFNESS = 195f

private val SeatWidth = 84.dp
private val SeatAvatar = 60.dp

@Composable
private fun MemberSeat(
    member: MemberUi,
    springIn: Boolean,
    modifier: Modifier = Modifier,
    leaving: Boolean = false,
    onDepart: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    val description = if (member.isHost) stringResource(R.string.party_member_host_description, member.firstName) else member.firstName
    val removeLabel = stringResource(R.string.remove_title, member.firstName)
    val reduce = LocalReduceMotion.current
    val depart by rememberUpdatedState(onDepart)
    // 0: 30% and transparent, 1: in place. Arrival springs 0 → 1, departure the same spring back to 0 (as on iOS).
    val presence = remember { Animatable(if (springIn && !reduce) 0f else 1f) }
    LaunchedEffect(leaving) {
        val target = if (leaving) 0f else 1f
        if (reduce) presence.snapTo(target) else presence.animateTo(target, spring(SEAT_SPRING_DAMPING, SEAT_SPRING_STIFFNESS))
        if (leaving) depart()
    }
    Column(
        modifier = modifier
            .width(SeatWidth)
            // A departing seat is already out of the party: no tag, no action, nothing to announce.
            .then(if (leaving) Modifier else Modifier.testTag(PartyTestTags.seat(member.userId)))
            // Host only, never on the host's own seat: tap to remove (after a confirmation).
            .then(
                if (onRemove == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = removeLabel,
                        onClick = onRemove
                    )
                }
            )
            .clearAndSetSemantics { if (!leaving) contentDescription = description }
            // The whole seat (avatar and name) springs, as on iOS.
            .graphicsLayer {
                val p = presence.value
                scaleX = SEAT_FROM_SCALE + (1f - SEAT_FROM_SCALE) * p
                scaleY = scaleX
                alpha = p.coerceIn(0f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            Avatar(member.avatar, member.firstName, size = SeatAvatar)
            if (member.isHost) HostBadge(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
        }
        Spacer(Modifier.height(if (member.isHost) 2.dp else 0.dp))
        Text(member.firstName, style = Type.Body, color = Brand.Ink, maxLines = 1)
    }
}

@Composable
private fun HostBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black, CircleShape)
            .border(2.dp, Brand.Card, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(stringResource(R.string.party_host_badge), style = Type.TabLabelSelected, color = Color.White)
    }
}

@Composable
private fun EmptySeat(modifier: Modifier = Modifier) {
    val breathing = rememberBreathing()
    val description = stringResource(R.string.party_empty_seat)
    Column(
        modifier = modifier.width(SeatWidth).testTag(PartyTestTags.EMPTY_SEAT).clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(SeatAvatar)
                .graphicsLayer {
                    // Scale to 0.94 and fade, 0.9 s each way (iOS: the fill fades to 35%).
                    val p = breathing()
                    scaleX = 1f - EMPTY_SEAT_SHRINK * p
                    scaleY = scaleX
                    alpha = 1f - EMPTY_SEAT_FADE * p
                }
                .border(2.dp, Brand.Placeholder, CircleShape)
        )
        Text("", style = Type.Body)
    }
}

@Composable
private fun ReconnectingPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Brand.Card, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(PartyTestTags.RECONNECTING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActivitySpinner(size = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.party_reconnecting), style = Type.Body, color = Brand.InkSecondary)
    }
}

@Composable
private fun PartyOver(
    title: String,
    body: String,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    frozen: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(PartyTestTags.EXPIRED),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = Type.PlaceholderTitle, color = Brand.Ink, textAlign = TextAlign.Center)
            if (body.isNotEmpty()) Text(body, style = Type.Subtitle, color = Brand.InkSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.party_back_home), onClick = onHome)
        }
        if (frozen != null) {
            Spacer(Modifier.height(8.dp))
            frozen()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun PartyScreenPreview() {
    SplitPartyTheme {
        PartyScreen(
            PartyUiState(
                phase = PartyPhase.Open,
                joinCode = persistentListOf("🍕", "🍺", "🎉"),
                members = persistentListOf(MemberUi("annie", "Annie", "avatar_annie", isHost = true)),
                millisLeft = 299_000
            ),
            PaddingValues(),
            onBack = {}
        )
    }
}
