package com.splitparty.android.ui.party

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.splitparty.android.ui.components.rememberBreathingAlpha
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
    viewModel: PartyViewModel = koinViewModel(key = "party_$partyId") { parametersOf(partyId, userId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PartyEvent.MemberJoined -> view.partyHaptic(success = true)
            }
        }
    }
    PartyScreen(state, contentPadding, onBack = onHome, modifier = modifier)
}

/**
 * The Split Party screen (PoC-8/9), host and members alike: code, countdown,
 * live member row, and the area the total/tip/share slice fills in later.
 * Provisional layout (docs/design/visual-conventions.md, "Party screen").
 */
@Composable
fun PartyScreen(state: PartyUiState, contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset() + Dimens.HeaderTop, bottom = contentPadding.calculateBottomPadding())
    ) {
        PartyHeader(stringResource(R.string.party_title, DemoMerchant.NAME), onBack)
        when (state.phase) {
            PartyPhase.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { ActivitySpinner() }

            PartyPhase.Open -> OpenParty(state, Modifier.weight(1f))

            PartyPhase.Expired -> PartyOver(
                stringResource(R.string.party_expired_title),
                stringResource(R.string.party_expired_body),
                onBack
            )

            PartyPhase.Settled -> PartyOver(stringResource(R.string.party_settled_title), "", onBack)

            PartyPhase.NotFound -> PartyOver(stringResource(R.string.party_not_found), "", onBack)
        }
    }
}

@Composable
private fun OpenParty(state: PartyUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = Dimens.ContentSpacing, bottom = Dimens.ListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        if (state.reconnecting) ReconnectingPill(Modifier.align(Alignment.CenterHorizontally))
        CodeCard(state.joinCode, state.millisLeft)
        MembersCard(state.members)
        TotalPlaceholder()
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
private fun MembersCard(members: List<MemberUi>, modifier: Modifier = Modifier) {
    // Members present when the screen opened are simply there; later ones spring in.
    val initial = remember { members.map { it.userId }.toSet() }
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
            members.forEach { member -> MemberSeat(member, springIn = member.userId !in initial) }
            repeat((MIN_SEATS - members.size).coerceAtLeast(0)) { EmptySeat() }
        }
    }
}

private val SeatWidth = 84.dp
private val SeatAvatar = 60.dp

@Composable
private fun MemberSeat(member: MemberUi, springIn: Boolean, modifier: Modifier = Modifier) {
    val description = if (member.isHost) stringResource(R.string.party_member_host_description, member.firstName) else member.firstName
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (springIn && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Column(
        modifier = modifier
            .width(SeatWidth)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        ) {
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
    val breathing = rememberBreathingAlpha()
    val description = stringResource(R.string.party_empty_seat)
    Column(
        modifier = modifier.width(SeatWidth).testTag(PartyTestTags.EMPTY_SEAT).clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(SeatAvatar)
                .graphicsLayer { alpha = breathing() }
                .border(2.dp, Brand.Placeholder, CircleShape)
        )
        Text("", style = Type.Body)
    }
}

/** Reserved for PoC-10/11 (bill, tip, share, Pay). Nothing here reads or writes money. */
@Composable
private fun TotalPlaceholder(modifier: Modifier = Modifier) {
    PartyCard(modifier.testTag(PartyTestTags.TOTAL)) {
        Text(stringResource(R.string.party_waiting_for_total), style = Type.SectionTitle, color = Brand.Ink)
        Text(
            stringResource(R.string.party_waiting_for_total_hint),
            style = Type.Body,
            color = Brand.InkSecondary,
            textAlign = TextAlign.Center
        )
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
private fun PartyOver(title: String, body: String, onHome: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp).testTag(PartyTestTags.EXPIRED),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = Type.PlaceholderTitle, color = Brand.Ink, textAlign = TextAlign.Center)
        if (body.isNotEmpty()) Text(body, style = Type.Subtitle, color = Brand.InkSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        PillButton(stringResource(R.string.party_back_home), onClick = onHome)
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
