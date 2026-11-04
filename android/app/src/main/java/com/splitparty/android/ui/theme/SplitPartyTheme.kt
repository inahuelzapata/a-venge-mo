package com.splitparty.android.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max

/**
 * Always the light design: the iOS captures have no dark appearance, so
 * Android ignores the system theme (no dynamic colour, no inversion).
 */
private val colors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    background = Brand.Page,
    onBackground = Brand.Ink,
    surface = Brand.Card,
    onSurface = Brand.Ink,
    onSurfaceVariant = Brand.InkSecondary,
    surfaceVariant = Brand.Chip,
    surfaceTint = Color.Transparent,
    outline = Brand.CapsuleOutline,
    error = Brand.Error
)

private val typography = Typography().run {
    copy(
        bodyLarge = Type.Subtitle,
        bodyMedium = Type.Body,
        bodySmall = Type.Footer,
        titleLarge = Type.ScreenTitle,
        labelLarge = Type.Button,
        labelSmall = Type.TabLabel
    )
}

/** iOS shows no touch ripple or highlight; the only press feedback is the pill button's own dimming. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0
}

@Composable
fun SplitPartyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalRippleConfiguration provides null,
            LocalTextStyle provides Type.Body,
            content = content
        )
    }
}

/** The smallest top/bottom insets content gets: the reference iPhone's safe area. */
@Immutable
data class MinimumInsets(val top: Dp = Dimens.ReferenceTopInset, val bottom: Dp = Dimens.ReferenceBottomInset)

/** Screenshot tests that render part of a screen (e.g. above a keyboard stand-in) override this. */
val LocalMinimumInsets = staticCompositionLocalOf { MinimumInsets() }

/**
 * Top edge for content: the device's status bar / cutout inset, but never less
 * than the reference iPhone's 62, so headers sit at the same height on every
 * demo phone and clear any cutout.
 */
@Composable
fun topInset(): Dp = max(WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(), LocalMinimumInsets.current.top)

/** Bottom edge: the gesture/navigation area, never less than the reference iPhone's 34. */
@Composable
fun bottomInset(): Dp = max(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), LocalMinimumInsets.current.bottom)
