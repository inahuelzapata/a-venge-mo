package com.splitparty.android.ui.party

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitparty.android.R
import com.splitparty.android.format.Money
import com.splitparty.android.ui.components.ActivitySpinner
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.Type
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PRESSED_ALPHA = 0.8f

/** What the host's button reads ("Collect · $x"): the amount when ready, otherwise why it is disabled. */
@Composable
private fun payLabel(pay: PayState, collectCents: Long?): String = when (pay) {
    PayState.Ready -> collectCents?.let { stringResource(R.string.pay_action_amount, Money.format(it)) }
        ?: stringResource(R.string.pay_action)

    PayState.NeedsTotal -> stringResource(R.string.pay_needs_total)

    PayState.NeedsMembers -> stringResource(R.string.pay_needs_members)

    PayState.Expired -> stringResource(R.string.pay_expired)

    PayState.Cancelled -> stringResource(R.string.pay_cancelled)
}

/**
 * The host's Pay button (PoC-11, labelled "Collect"), the bottom of the total area: brand blue
 * capsule, no ripple or elevation (a press dims it). Disabled it is grey and
 * its label is the reason. No second confirmation: joining was consent.
 */
@Composable
fun PayButton(pay: PayState, collectCents: Long?, paying: Boolean, onPay: () -> Unit, modifier: Modifier = Modifier) {
    val enabled = pay == PayState.Ready && !paying
    val label = payLabel(pay, collectCents)
    val spoken = if (pay == PayState.Ready) {
        stringResource(R.string.pay_description_ready, Money.spoken(collectCents ?: 0L))
    } else {
        stringResource(R.string.pay_description_disabled, label)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PillHeight)
            .alpha(if (pressed) PRESSED_ALPHA else 1f)
            .background(if (pay == PayState.Ready) Brand.Blue else Brand.ButtonDisabled, CircleShape)
            .testTag(PartyTestTags.PAY)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onPay)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                if (enabled) {
                    onClick {
                        onPay()
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (paying) ActivitySpinner(color = Color.White) else Text(label, style = Type.Button, color = Color.White)
    }
}

// The same sequence as iOS: 100 ms, the ticks 280 ms apart, 150 ms, then the
// check and a 2.4 s confetti rain from the top of the screen.
private const val TICK_STAGGER_MS = 280L
private const val TICK_LEAD_MS = 100L
private const val SUCCESS_LEAD_MS = 150L
private const val CONFETTI_MS = 2_400

/** SwiftUI spring(response: 0.3) as a Compose stiffness: (2π / 0.3)². The tick badges. */
private const val TICK_STIFFNESS = 439f

/** SwiftUI spring(response: 0.4) as a Compose stiffness: (2π / 0.4)². */
private const val CHECK_STIFFNESS = 247f
private const val CHECK_SIZE_DP = 76

/**
 * The paid party (PoC-11, FR-5.3), on every phone: the seats tick to paid one
 * by one, then a confetti burst and the success check, the amount collected
 * (host) or paid (member) and the way back to Home. It plays once, when this
 * screen saw the party go from open to paid ([SettlementUi.animate]); opened
 * later, or with animations removed, everything is simply there (haptics stay).
 */
@Composable
fun SettledParty(state: PartyUiState, onHome: () -> Unit, modifier: Modifier = Modifier) {
    val settlement = state.settlement ?: return
    val reduce = LocalReduceMotion.current
    val view = LocalView.current
    val payers = state.members.filterNot { it.isHost }
    val playing = settlement.animate && settlement.amountCents != null
    // Saveable so a rotation or a trip through the back stack never replays it.
    var ticked by rememberSaveable { mutableIntStateOf(if (playing) 0 else payers.size) }
    var played by rememberSaveable { mutableIntStateOf(if (playing) 0 else 1) }
    val checkScale = remember { Animatable(if (played == 1) 1f else 0f) }
    val confetti = remember { Animatable(if (played == 1) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played == 1) return@LaunchedEffect
        if (reduce) {
            ticked = payers.size
            checkScale.snapTo(1f)
            confetti.snapTo(1f)
        } else {
            delay(TICK_LEAD_MS)
            for (i in payers.indices) {
                if (i > 0) delay(TICK_STAGGER_MS)
                ticked = i + 1
                view.shareTick()
            }
            delay(SUCCESS_LEAD_MS)
            launch { confetti.animateTo(1f, tween(CONFETTI_MS, easing = LinearEasing)) }
            checkScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = CHECK_STIFFNESS))
        }
        view.partyHaptic(success = true)
        played = 1
    }
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(PartyTestTags.SETTLED),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Dimens.ContentSpacing))
            PaidSeats(state.members, payers.take(ticked).map { it.userId }.toSet())
            Spacer(Modifier.height(Dimens.CardGap))
            SuccessCard(settlement, state.hostName, checkScale.value, onHome)
        }
        // Over the whole screen, from the top, as on iOS. Never with motion removed.
        val rain = confetti.value
        if (!reduce && rain in 0.001f..0.999f) ConfettiRain(rain, Modifier.fillMaxSize())
    }
}

@Composable
private fun PaidSeats(members: List<MemberUi>, ticked: Set<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding)
            .testTag(PartyTestTags.PAID_SEATS),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        members.forEach { member ->
            val paid = member.userId in ticked
            val spoken = when {
                member.isHost -> stringResource(R.string.party_member_host_description, member.firstName)
                paid -> stringResource(R.string.settle_member_paid, member.firstName)
                else -> stringResource(R.string.settle_member_pending, member.firstName)
            }
            Column(
                Modifier
                    .width(84.dp)
                    .testTag(PartyTestTags.paidSeat(member.userId, paid))
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Avatar(member.avatar, member.firstName, size = 56.dp)
                    if (!member.isHost) PaidTick(paid, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
                }
                Text(member.firstName, style = Type.Body, color = Brand.Ink, maxLines = 1)
            }
        }
    }
}

/** A green check badge that pops in when the member's share lands; a grey ring while pending. */
@Composable
private fun PaidTick(paid: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (paid) 1f else 0f) }
    LaunchedEffect(paid) {
        val target = if (paid) 1f else 0f
        if (reduce) scale.snapTo(target) else scale.animateTo(target, spring(dampingRatio = 0.55f, stiffness = TICK_STIFFNESS))
    }
    Box(modifier.size(22.dp)) {
        Box(Modifier.fillMaxSize().border(2.dp, Brand.Card, CircleShape).background(Brand.Chip, CircleShape))
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .border(2.dp, Brand.Card, CircleShape)
                .background(Brand.Success, CircleShape)
        ) { CheckMark(stroke = 2.dp, modifier = Modifier.fillMaxSize().padding(6.dp)) }
    }
}

@Composable
private fun SuccessCard(settlement: SettlementUi, hostName: String, checkScale: Float, onHome: () -> Unit) {
    val amount = settlement.amountCents
    Column(
        Modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(CHECK_SIZE_DP.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    }
                    .background(Brand.Success, CircleShape)
                    .testTag(PartyTestTags.SUCCESS_CHECK)
            ) { CheckMark(stroke = 6.dp, modifier = Modifier.fillMaxSize().padding(20.dp)) }
        }
        if (amount == null) {
            Text(
                stringResource(R.string.party_settled_title),
                style = Type.PlaceholderTitle,
                color = Brand.Ink,
                textAlign = TextAlign.Center
            )
        } else {
            val spoken = stringResource(
                if (settlement.isHost) R.string.settle_collected_description else R.string.settle_paid_description,
                Money.spoken(amount)
            )
            Column(
                Modifier.testTag(PartyTestTags.SUCCESS_AMOUNT).clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(if (settlement.isHost) R.string.settle_collected_title else R.string.settle_paid_title),
                    style = Type.Body,
                    color = Brand.InkSecondary
                )
                Text(Money.format(amount), style = Type.Balance, color = Brand.Ink)
            }
            val body = if (settlement.isHost) {
                stringResource(R.string.settle_collected_body)
            } else {
                stringResource(R.string.settle_paid_body, hostName)
            }
            Text(
                body,
                style = Type.Subtitle,
                color = Brand.InkSecondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(4.dp))
        PillButton(stringResource(R.string.party_back_home), onClick = onHome, modifier = Modifier.testTag(PartyTestTags.SETTLED_HOME))
    }
}

@Composable
private fun CheckMark(stroke: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.05f, size.height * 0.55f)
            lineTo(size.width * 0.38f, size.height * 0.85f)
            lineTo(size.width * 0.95f, size.height * 0.18f)
        }
        drawPath(path, Color.White, style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val ConfettiColors = listOf(
    Brand.Blue,
    Color(0xFFFFC83D),
    Color(0xFFFF5C8A),
    Color(0xFF2BC48A),
    Color(0xFF6CC4FF)
)
private const val CONFETTI_PIECES = 90
private const val CONFETTI_SEED = 7

/** dp per second², as iOS's 900 pt/s². */
private const val GRAVITY_DP = 900f
private const val CONFETTI_SECONDS = CONFETTI_MS / 1_000f

/** Fully visible for the first 60% of the rain, then fading out. */
private const val CONFETTI_FADE_FROM = 0.6f

private data class Piece(
    val startX: Float,
    val horizontalSpeed: Float,
    val verticalSpeed: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color
)

private fun Random.between(from: Float, to: Float) = from + nextFloat() * (to - from)

/**
 * The iOS confetti, piece for piece in kind: 90 pieces launched up from just
 * above the top edge, spreading sideways, falling with gravity and spinning,
 * fading over the last 40% of 2.4 s. [progress] goes 0 → 1 over that time.
 */
@Composable
private fun ConfettiRain(progress: Float, modifier: Modifier = Modifier) {
    val pieces = remember {
        val random = Random(CONFETTI_SEED)
        List(CONFETTI_PIECES) {
            Piece(
                startX = random.between(0.1f, 0.9f),
                horizontalSpeed = random.between(-160f, 160f),
                verticalSpeed = random.between(-420f, -120f),
                spin = random.between(-540f, 540f),
                width = random.between(6f, 10f),
                height = random.between(10f, 16f),
                color = ConfettiColors[it % ConfettiColors.size]
            )
        }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val t = progress * CONFETTI_SECONDS
        val alpha = if (progress < CONFETTI_FADE_FROM) 1f else (1f - progress) / (1f - CONFETTI_FADE_FROM)
        val corner = CornerRadius(1.5.dp.toPx())
        pieces.forEach { piece ->
            val x = piece.startX * size.width + (piece.horizontalSpeed * t).dp.toPx()
            val y = (-20f + piece.verticalSpeed * t + GRAVITY_DP * t * t / 2).dp.toPx()
            val w = piece.width.dp.toPx()
            val h = piece.height.dp.toPx()
            rotate(piece.spin * t, Offset(x, y)) {
                drawRoundRect(
                    piece.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(x - w / 2, y - h / 2),
                    size = Size(w, h),
                    cornerRadius = corner
                )
            }
        }
    }
}
