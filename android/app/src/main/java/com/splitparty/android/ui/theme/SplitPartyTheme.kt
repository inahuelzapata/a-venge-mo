package com.splitparty.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    error = Brand.Error
)

@Composable
fun SplitPartyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
