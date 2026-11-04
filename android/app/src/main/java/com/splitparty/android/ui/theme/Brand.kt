package com.splitparty.android.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette taken from docs/design/home-feed-reference-*.jpg. */
object Brand {
    val Blue = Color(0xFF0B5FD6)
    val Page = Color(0xFFF2F3F5)
    val Card = Color.White
    val Ink = Color(0xFF111111)
    val InkSecondary = Color(0xFF8A8A8E)
    val Chip = Color(0xFFF2F2F4)
    val Outline = Color(0xFFE3E4E8)
    val Error = Color(0xFFD32F2F)
    val Highlight = Color(0xFFDCE8FB)
    val Link = Color(0xFF1A73E8)

    /** Navy of the Aven-mo mark artwork (brand/aven-mo-mark.svg). */
    val MarkNavy = Color(0xFF021F40)

    /**
     * Centre Pay/Request button. The same blue as iOS (Theme.brandBlue, #0087E0)
     * rather than [Blue], because this button must match iOS exactly
     * (docs/design/README.md "Brand mark").
     */
    val PayButtonBlue = Color(0xFF0087E0)
}
