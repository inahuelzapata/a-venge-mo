package com.splitparty.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.LocalReduceMotion

private const val PULSE_MS = 1_200
private const val PULSE_SCALE = 2.6f
private const val PULSE_ALPHA = 0.4f
private const val SHIMMER_MS = 900

/**
 * The "live" dot of the Join banner and the party screen: a solid dot with a
 * halo that grows to 2.6× and fades, 1.2 s ease-out on repeat (as on iOS).
 * Without motion, just the dot.
 */
@Composable
fun PulsingDot(modifier: Modifier = Modifier, color: Color = Brand.Blue, size: Dp = 10.dp) {
    val reduce = LocalReduceMotion.current
    Box(modifier.size(size * 2), contentAlignment = Alignment.Center) {
        if (!reduce) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val t by transition.animateFloat(
                0f,
                1f,
                infiniteRepeatable(tween(PULSE_MS, easing = EaseOut), RepeatMode.Restart),
                label = "pulseT"
            )
            // The halo may draw past the box: graphicsLayer does not clip.
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = 1f + t * (PULSE_SCALE - 1f)
                        scaleY = scaleX
                        alpha = PULSE_ALPHA * (1f - t)
                    }
                    .background(color, CircleShape)
            )
        }
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/**
 * A gentle repeating pulse (empty seats while waiting): 0 → 1 → 0, 0.9 s each
 * way, ease-in-out. Always 0 without motion.
 */
@Composable
fun rememberBreathing(): () -> Float {
    if (LocalReduceMotion.current) return { 0f }
    val transition = rememberInfiniteTransition(label = "breathing")
    val progress = transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(BREATHING_MS, easing = EaseInOut), RepeatMode.Reverse),
        label = "breathing"
    )
    return { progress.value }
}

/**
 * 0 → 1 once, shortly after the caller first appears (the merchant card's
 * sheen: 0.25 s, then 0.9 s ease-in-out, as on iOS). Stays 0 without motion
 * or when not [enabled].
 */
@Composable
fun rememberShimmerProgress(enabled: Boolean = true): () -> Float {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduce, enabled) {
        if (!reduce && enabled) progress.animateTo(1f, tween(SHIMMER_MS, delayMillis = SHIMMER_DELAY_MS, easing = EaseInOut))
    }
    return { progress.value }
}

/**
 * A light band swept across a card at [progress] (0..1, nothing drawn at
 * either end). [horizontalInset] and [radius] describe the card inside the
 * modified area so the sheen stays on the card.
 */
fun Modifier.shimmer(progress: () -> Float, horizontalInset: Dp, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val p = progress()
    if (p <= 0f || p >= 1f) return@drawWithContent
    val inset = horizontalInset.toPx()
    val band = size.width * SHIMMER_BAND
    val x = -band + (size.width + band * 2) * p
    val card = Path().apply {
        addRoundRect(RoundRect(inset, 0f, size.width - inset, size.height, CornerRadius(radius.toPx())))
    }
    clipPath(card) {
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = SHIMMER_ALPHA), Color.White.copy(alpha = 0f)),
                start = Offset(x - band, 0f),
                end = Offset(x + band, size.height * SHIMMER_SLANT)
            )
        )
    }
}

private const val BREATHING_MS = 900
private const val SHIMMER_DELAY_MS = 250
private const val SHIMMER_BAND = 0.35f
private const val SHIMMER_ALPHA = 0.55f
private const val SHIMMER_SLANT = 0.4f
