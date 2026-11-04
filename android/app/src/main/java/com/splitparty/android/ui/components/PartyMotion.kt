package com.splitparty.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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

private const val PULSE_MS = 1_100
private const val SHIMMER_MS = 1_000

/**
 * The "live" dot of the Join banner and the party screen: a solid dot with a
 * halo that grows and fades on repeat. Without motion, just the dot.
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
                infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "pulseT"
            )
            Box(
                Modifier
                    .size(size * 2)
                    .graphicsLayer {
                        scaleX = 0.5f + t * 0.5f
                        scaleY = scaleX
                        alpha = 0.45f * (1f - t)
                    }
                    .background(color, CircleShape)
            )
        }
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/** Opacity for a gentle repeating pulse (empty seats while waiting): 1 → 0.45 and back. Always 1 without motion. */
@Composable
fun rememberBreathingAlpha(): () -> Float {
    if (LocalReduceMotion.current) return { 1f }
    val transition = rememberInfiniteTransition(label = "breathing")
    val alpha = transition.animateFloat(
        1f,
        BREATHING_MIN_ALPHA,
        infiniteRepeatable(tween(BREATHING_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathingAlpha"
    )
    return { alpha.value }
}

/** 0 → 1 once, shortly after the caller first appears (the merchant card's sheen). Stays 0 without motion. */
@Composable
fun rememberShimmerProgress(): () -> Float {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduce) {
        if (!reduce) progress.animateTo(1f, tween(SHIMMER_MS, delayMillis = SHIMMER_DELAY_MS, easing = LinearEasing))
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
private const val BREATHING_MIN_ALPHA = 0.45f
private const val SHIMMER_DELAY_MS = 250
private const val SHIMMER_BAND = 0.35f
private const val SHIMMER_ALPHA = 0.55f
private const val SHIMMER_SLANT = 0.4f
