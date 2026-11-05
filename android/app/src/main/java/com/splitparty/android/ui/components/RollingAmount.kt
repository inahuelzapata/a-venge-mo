package com.splitparty.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.splitparty.android.format.Money
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.Type

private const val ROLL_MS = 600
private const val ROLL_STEPS = 1_000L

/**
 * An amount in cents (a share, a total, a balance), rolling from the previous value to [cents] (0.6 s,
 * ease out). Interpolation is integer cents over a progress fraction, so the
 * shown amount is always a whole number of cents, negative values included.
 * Instant with reduced motion.
 */
@Composable
fun RollingAmount(cents: Long, modifier: Modifier = Modifier, style: TextStyle = Type.Balance) {
    val reduce = LocalReduceMotion.current
    // Saveable: coming back to a screen (a tab switch restores state) rolls from the value last shown there.
    var from by rememberSaveable { mutableLongStateOf(cents) }
    var target by rememberSaveable { mutableLongStateOf(cents) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(cents, reduce) {
        if (cents == target) return@LaunchedEffect
        // Restart from whatever is on screen right now.
        from = shown(from, target, progress.value)
        target = cents
        if (reduce) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ROLL_MS, easing = FastOutSlowInEasing))
        }
    }
    Text(Money.format(shown(from, target, progress.value)), style = style, color = Brand.Ink, modifier = modifier)
}

private fun shown(from: Long, to: Long, progress: Float): Long = from + (to - from) * (progress * ROLL_STEPS).toLong() / ROLL_STEPS
