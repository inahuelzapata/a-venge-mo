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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitparty.android.ui.theme.Brand

/**
 * `users.avatar` is an asset key (docs/firestore-schema.md). No avatar images
 * are bundled yet, so each known key maps to a fixed colour with the user's
 * initial; unknown keys fall back to grey initials, as the schema allows.
 */
private val avatarColors = mapOf(
    "avatar_maxi" to Color(0xFF3D7BE0),
    "avatar_annie" to Color(0xFFE86A5C),
    "avatar_christophe" to Color(0xFF2FA37A)
)
private val fallbackColor = Color(0xFF9E9EA6)

@Composable
fun Avatar(avatarKey: String?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).background(avatarColors[avatarKey] ?: fallbackColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.45f).sp,
            lineHeight = (size.value * 0.45f).sp
        )
    }
}

/** The white outlined initial circle at the top-left of the reference header. */
@Composable
fun InitialBadge(name: String, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Box(
        modifier = modifier.size(size).background(Brand.Outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(size - 2.dp).background(Brand.Card, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "", fontSize = 24.sp, color = Brand.Ink)
        }
    }
}
