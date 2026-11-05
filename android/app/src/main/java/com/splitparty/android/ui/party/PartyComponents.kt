package com.splitparty.android.ui.party

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitparty.android.R
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.Type
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val POP_STAGGER_MS = 120L
private const val BOB_MS = 1_800
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Three emoji tiles. With [animated], they pop in one by one and then bob
 * gently, each a little out of phase. The caller sets the TalkBack label (the
 * emoji names); the tiles themselves are decorative.
 */
@Composable
fun EmojiCodeTiles(code: List<String>, modifier: Modifier = Modifier, tileSize: Dp = 76.dp, animated: Boolean = true) {
    val motion = animated && !LocalReduceMotion.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(tileSize * 0.16f)) {
        code.forEachIndexed { index, emoji ->
            if (motion) AnimatedTile(emoji, index, tileSize) else Tile(emoji, tileSize)
        }
    }
}

@Composable
private fun AnimatedTile(emoji: String, index: Int, size: Dp) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * POP_STAGGER_MS)
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(BOB_MS), RepeatMode.Restart),
        label = "bobT"
    )
    Tile(
        emoji,
        size,
        Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            translationY = sin((bob + index / 3f) * 2 * PI).toFloat() * 3.dp.toPx()
        }
    )
}

@Composable
private fun Tile(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(Brand.Chip, RoundedCornerShape(size * 0.26f)),
        contentAlignment = Alignment.Center
    ) {
        // System emoji font (Inter has no emoji); size only, no tracking.
        Text(emoji, style = TextStyle(fontSize = (size.value * 0.52f).sp, lineHeight = (size.value * 0.62f).sp))
    }
}

/** Header as Home's (44 tall, 8 below the safe area): round back button on the left, centred title. */
@Composable
fun PartyHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.HeaderHeight)
            .padding(horizontal = Dimens.HeaderMargin),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            style = Type.ScreenTitle,
            color = Brand.Ink,
            modifier = Modifier.padding(horizontal = Dimens.HeaderHeight + 8.dp).semantics { heading() }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(Dimens.HeaderHeight)
                .background(Brand.Card, CircleShape)
                .border(1.dp, Brand.CapsuleOutline, CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onBack)
                .testTag(PartyTestTags.BACK),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.back),
                tint = Brand.Ink,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Confirm (someone joined) or reject (wrong code). Haptics stay on with animations removed. */
fun View.partyHaptic(success: Boolean) {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> if (success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        success -> HapticFeedbackConstants.VIRTUAL_KEY
        else -> HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

/** A light tick when this member's share moves; kept with animations removed. */
fun View.shareTick() {
    val constant = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.R
    ) {
        HapticFeedbackConstants.SEGMENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(constant)
}

/** "4:59", never negative. */
fun formatCountdown(millisLeft: Long): String {
    // Rounded up, so it reads 5:00 at creation and 0:00 only once expired.
    val totalSeconds = (millisLeft.coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    return "%d:%02d".format(totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
}

object PartyTestTags {
    const val BACK = "party_back"
    const val CODE = "party_code"
    const val COUNTDOWN = "party_countdown"
    const val MEMBERS = "party_members"
    const val EMPTY_SEAT = "party_empty_seat"
    const val RECONNECTING = "party_reconnecting"
    const val EXPIRED = "party_expired"
    const val TOTAL = "party_total"
    const val SCAN = "party_scan"
    const val ENTER = "party_enter_total"
    const val SHARE = "party_share"
    const val TOTAL_VALUE = "party_total_value"
    const val HEADCOUNT = "party_headcount"
    const val TIP_CHIPS = "party_tip_chips"
    const val WRITE_ERROR = "party_write_error"
    const val LEAVE = "party_leave"

    const val DIALOG = "party_dialog"
    const val DIALOG_CONFIRM = "party_dialog_confirm"

    fun tipChip(label: String) = "party_tip_$label"

    fun seat(userId: String) = "party_seat_$userId"
}
