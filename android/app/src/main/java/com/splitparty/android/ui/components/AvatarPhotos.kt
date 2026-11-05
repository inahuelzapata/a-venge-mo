package com.splitparty.android.ui.components

import androidx.compose.ui.graphics.Color
import com.splitparty.android.domain.model.AvatarCrop

/**
 * What a `users.avatar` value draws: the photo to fetch ([url], null for initials
 * only), how to crop it, and the colour behind the initials shown while the photo
 * is loading or can't be shown.
 */
data class AvatarSpec(val url: String?, val crop: AvatarCrop, val color: Color)

private fun tallerPhoto(file: String) = "https://taller.ai/_next/image?url=%2Fleadership%2F$file.jpg&w=1080&q=75"

private data class SeededPerson(val key: String, val url: String, val crop: AvatarCrop, val color: Color)

/** Avatar values the app understands (docs/firestore-schema.md, docs/design/visual-conventions.md). */
object AvatarPhotos {
    val FallbackColor = Color(0xFF8E8E93)

    private val seeded = listOf(
        SeededPerson("avatar_maxi", tallerPhoto("maximiliano-armesto"), AvatarCrop(0.50f, 0.40f, 0.70f), Color(0xFF0087E0)),
        SeededPerson("avatar_annie", tallerPhoto("annie-rodriguez"), AvatarCrop(0.55f, 0.40f, 0.70f), Color(0xFFE5487D)),
        SeededPerson("avatar_christophe", tallerPhoto("christophe-kolb"), AvatarCrop(0.50f, 0.35f, 1.00f), Color(0xFF2EAF6F))
    )
    private val byValue = seeded.flatMap { listOf(it.key to it, it.url to it) }.toMap()

    /**
     * The seed stores each person's taller.ai URL in `users.avatar`; older data and
     * fixtures carry the `avatar_*` key. Both resolve to that person's photo, crop
     * and colour. Any other https URL is loaded with a centred crop over grey
     * initials; anything else (null, empty, unknown key) is grey initials only.
     */
    fun specFor(avatar: String?): AvatarSpec {
        byValue[avatar]?.let { return AvatarSpec(it.url, it.crop, it.color) }
        val url = avatar?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) && it.length > "https://".length }
        return AvatarSpec(url, AvatarCrop.Centered, FallbackColor)
    }
}
