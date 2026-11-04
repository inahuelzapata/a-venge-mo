package com.splitparty.android.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the system animator scale is 0 ("Remove animations" or the
 * developer option): pop-ins, bobs, springs, pulses and shakes become instant
 * state changes. Haptics never depend on it. Screenshot tests set it to show
 * the settled state.
 */
val LocalReduceMotion = staticCompositionLocalOf { !ValueAnimator.areAnimatorsEnabled() }

/** [spec], or an instant snap when motion is reduced. */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (LocalReduceMotion.current) snap() else spec
