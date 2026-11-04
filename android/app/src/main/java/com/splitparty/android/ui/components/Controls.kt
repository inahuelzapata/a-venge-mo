package com.splitparty.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type

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
