package com.splitparty.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.splitparty.android.ui.theme.Inter

/**
 * `users.avatar` is an asset key (docs/firestore-schema.md). No avatar images
 * exist yet, so each seeded key is a coloured circle with white initials, in the
 * colours of the iOS captures (docs/design/visual-conventions.md); unknown keys
 * fall back to grey.
 */
private val avatarColors = mapOf(
    "avatar_maxi" to Color(0xFF0087E0),
    "avatar_annie" to Color(0xFFE5487D),
    "avatar_christophe" to Color(0xFF2EAF6F)
)
private val fallbackColor = Color(0xFF8E8E93)

/** Initials are 42% of the circle, semibold, as on iOS. */
private const val INITIALS_SCALE = 0.42f

@Composable
fun Avatar(avatarKey: String?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).background(avatarColors[avatarKey] ?: fallbackColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = (size.value * INITIALS_SCALE).sp
        Text(
            text = initials(name),
            color = Color.White,
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                lineHeight = fontSize,
                letterSpacing = 0.em
            )
        )
    }
}

/** "Maxi" → "M", "Julie Wang" → "JW", "" → "?" (same rule as iOS). */
fun initials(displayName: String): String {
    val letters = displayName.split(' ').filter { it.isNotEmpty() }.take(2).map { it.first() }
    return if (letters.isEmpty()) "?" else letters.joinToString("").uppercase()
}
