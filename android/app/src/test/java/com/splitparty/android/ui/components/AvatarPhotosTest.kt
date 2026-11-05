package com.splitparty.android.ui.components

import androidx.compose.ui.graphics.Color
import com.splitparty.android.domain.model.AvatarCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** users.avatar → photo, crop and colour (docs/design/visual-conventions.md). */
class AvatarPhotosTest {
    private fun photo(file: String) = "https://taller.ai/_next/image?url=%2Fleadership%2F$file.jpg&w=1080&q=75"

    @Test
    fun eachSeededKeyAndItsSeededUrlShowThatPersonsPhoto() {
        val people = listOf(
            Triple("avatar_maxi", photo("maximiliano-armesto"), Color(0xFF0087E0)),
            Triple("avatar_annie", photo("annie-rodriguez"), Color(0xFFE5487D)),
            Triple("avatar_christophe", photo("christophe-kolb"), Color(0xFF2EAF6F))
        )
        for ((key, url, color) in people) {
            val byKey = AvatarPhotos.specFor(key)
            assertEquals(key, url, byKey.url)
            assertEquals(key, color, byKey.color)
            // The seed now stores the URL itself: same photo, crop and colour.
            assertEquals(url, byKey, AvatarPhotos.specFor(url))
        }
    }

    @Test
    fun theSeededCropsAreTheOnesSharedWithIos() {
        assertEquals(AvatarCrop(0.50f, 0.40f, 0.70f), AvatarPhotos.specFor("avatar_maxi").crop)
        assertEquals(AvatarCrop(0.55f, 0.40f, 0.70f), AvatarPhotos.specFor("avatar_annie").crop)
        assertEquals(AvatarCrop(0.50f, 0.35f, 1.00f), AvatarPhotos.specFor("avatar_christophe").crop)
    }

    @Test
    fun unknownMissingOrNonUrlValuesAreGreyInitials() {
        for (value in listOf(null, "", "  ", "avatar_julie", "maxi", "http://example.com/a.jpg", "https://")) {
            val spec = AvatarPhotos.specFor(value)
            assertNull("$value", spec.url)
            assertEquals("$value", AvatarPhotos.FallbackColor, spec.color)
        }
    }

    @Test
    fun anyOtherHttpsUrlIsLoadedCentredOverGrey() {
        val spec = AvatarPhotos.specFor("https://example.com/julie.jpg")
        assertEquals("https://example.com/julie.jpg", spec.url)
        assertEquals(AvatarCrop.Centered, spec.crop)
        assertEquals(AvatarPhotos.FallbackColor, spec.color)
    }
}
