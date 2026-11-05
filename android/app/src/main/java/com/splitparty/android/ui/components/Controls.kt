package com.splitparty.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.motionSpec

private const val PRESSED_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.3f

/**
 * The black full-width capsule of the iOS captures ("Sign in", "Log out"): no
 * ripple or elevation. A press only dims it to 80%, and when disabled it is black at 30%.
 */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // While busy the button can't be pressed, but it stays black so the white spinner shows.
    val fillAlpha = if (enabled || busy) 1f else DISABLED_ALPHA
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PillHeight)
            .alpha(if (pressed) PRESSED_ALPHA else 1f)
            .background(Color.Black.copy(alpha = fillAlpha), CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) ActivitySpinner(color = Color.White) else Text(text, style = Type.Button, color = Color.White)
    }
}

/**
 * The secondary capsule: chip grey with black text, same size and press
 * behaviour as [PillButton] (dims, no ripple). [height] lets small inline
 * actions use the same look.
 */
@Composable
fun SoftPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Dimens.PillHeight,
    style: TextStyle = Type.Button
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(height)
            .alpha(
                if (pressed) {
                    PRESSED_ALPHA
                } else if (enabled) {
                    1f
                } else {
                    DISABLED_ALPHA
                }
            )
            .background(Brand.Chip, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = style, color = Brand.Ink, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

private const val SPOKES = 8
private const val SPIN_MILLIS = 800

/** The iOS activity indicator: eight spokes with a fading tail, turning in steps. */
@Composable
fun ActivitySpinner(modifier: Modifier = Modifier, color: Color = Brand.InkSecondary, size: Dp = 20.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = SPOKES.toFloat(),
        animationSpec = infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerStep"
    )
    Canvas(modifier.size(size)) {
        val head = step.toInt()
        val radius = this.size.minDimension / 2
        val stroke = radius * 0.22f
        for (i in 0 until SPOKES) {
            val age = (head - i + SPOKES) % SPOKES
            rotate(degrees = i * 360f / SPOKES) {
                drawLine(
                    color = color.copy(alpha = 1f - age * 0.1f),
                    start = Offset(center.x, center.y - radius * 0.45f),
                    end = Offset(center.x, center.y - radius + stroke / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private val SwitchOn = Color(0xFF34C759)
private val SwitchOff = Color(0xFFE9E9EA)

/**
 * The iOS switch (51 × 31 track, 27 white knob): drawn by hand so no Material
 * Switch colours, ripple or thumb icon leak in. Purely visual; the row around
 * it carries the toggle semantics.
 */
@Composable
fun IosSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val knobX by animateDpAsState(if (on) 22.dp else 2.dp, motionSpec(tween(200)), label = "switchKnob")
    Box(
        modifier = modifier
            .size(51.dp, 31.dp)
            .background(if (on) SwitchOn else SwitchOff, CircleShape)
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(27.dp)
                .dropShadow(CircleShape, Shadow(radius = 4.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.15f)))
                .background(Color.White, CircleShape)
        )
    }
}
