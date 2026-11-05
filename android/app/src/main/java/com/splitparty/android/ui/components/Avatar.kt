package com.splitparty.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.splitparty.android.data.AvatarImageStore
import com.splitparty.android.ui.theme.Inter
import kotlinx.coroutines.delay

/**
 * Where avatar photos come from. Null (the default: previews, Paparazzi and most
 * UI tests) draws initials only; MainActivity provides the app's store.
 */
val LocalAvatarImages = staticCompositionLocalOf<AvatarImageStore?> { null }

/** Initials are 42% of the circle, semibold, as on iOS. */
private const val INITIALS_SCALE = 0.42f

/** While a photo can't be fetched (offline, server down) the bubble keeps retrying. */
private const val FIRST_RETRY_MS = 5_000L
private const val MAX_RETRY_MS = 60_000L

const val AVATAR_PHOTO_TAG = "avatarPhoto"

/**
 * A person's bubble for a `users.avatar` value (docs/design/visual-conventions.md):
 * their photo, square-cropped and clipped to the circle, over coloured initials
 * that show while the photo loads or whenever it can't be shown. Values that aren't
 * a photo draw initials on grey.
 */
@Composable
fun Avatar(avatarKey: String?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val spec = remember(avatarKey) { AvatarPhotos.specFor(avatarKey) }
    val photo = avatarPhoto(spec)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(spec.color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            val image = remember(photo) { photo.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag(AVATAR_PHOTO_TAG)
            )
        } else {
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
}

/** The photo for [spec] once it is available: memory at once, else disk or network, retrying on failure. */
@Composable
private fun avatarPhoto(spec: AvatarSpec): Bitmap? {
    val store = LocalAvatarImages.current
    val url = spec.url
    if (store == null || url == null) return null
    val photo by produceState(store.cached(url, spec.crop), store, url, spec.crop) {
        var wait = FIRST_RETRY_MS
        while (value == null) {
            value = store.load(url, spec.crop)
            if (value == null) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }
    }
    return photo
}

/** "Maxi" → "M", "Julie Wang" → "JW", "" → "?" (same rule as iOS). */
fun initials(displayName: String): String {
    val letters = displayName.split(' ').filter { it.isNotEmpty() }.take(2).map { it.first() }
    return if (letters.isEmpty()) "?" else letters.joinToString("").uppercase()
}
