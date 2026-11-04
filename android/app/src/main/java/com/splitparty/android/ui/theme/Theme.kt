package com.splitparty.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette taken from docs/design/home-feed-reference-*.jpg. */
object Brand {
    val Blue = Color(0xFF0B5FD6)
    val BlueDark = Color(0xFF0849A8)
    val Page = Color(0xFFF2F3F5)
    val Card = Color.White
    val Ink = Color(0xFF111111)
    val InkSecondary = Color(0xFF8A8A8E)
    val Chip = Color(0xFFF2F2F4)
    val Outline = Color(0xFFE3E4E8)
    val Error = Color(0xFFD32F2F)
    val Highlight = Color(0xFFDCE8FB)
    val Link = Color(0xFF1A73E8)
}

private val colors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    background = Brand.Page,
    onBackground = Brand.Ink,
    surface = Brand.Card,
    onSurface = Brand.Ink,
    onSurfaceVariant = Brand.InkSecondary,
    surfaceVariant = Brand.Chip,
    outline = Brand.Outline,
    error = Brand.Error,
)

@Composable
fun SplitPartyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
