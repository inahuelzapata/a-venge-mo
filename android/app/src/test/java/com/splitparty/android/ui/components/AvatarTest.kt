package com.splitparty.android.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.data.AvatarFetcher
import com.splitparty.android.data.AvatarImageStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private var fetches = 0

    private fun show(avatar: String?, name: String, fetcher: AvatarFetcher) {
        val store = AvatarImageStore(folder.root, { url ->
            fetches++
            fetcher.fetch(url)
        })
        compose.setContent {
            CompositionLocalProvider(LocalAvatarImages provides store) {
                Avatar(avatar, name, size = 80.dp)
            }
        }
    }

    private val jpeg: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun anUnknownKeyIsInitialsAndNeverFetches() {
        show("avatar_julie", "Julie Wang") { jpeg }
        compose.onNodeWithText("JW").assertIsDisplayed()
        compose.onNodeWithTag(AVATAR_PHOTO_TAG).assertDoesNotExist()
        assertEquals(0, fetches)
    }

    @Test
    fun aSeededPersonShowsTheirPhotoOnceLoaded() {
        show("avatar_maxi", "Maxi") { jpeg }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(AVATAR_PHOTO_TAG)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(AVATAR_PHOTO_TAG).assertIsDisplayed()
        compose.onNodeWithText("M").assertDoesNotExist()
    }

    @Test
    fun aPhotoThatCannotBeFetchedKeepsTheInitials() {
        show("avatar_annie", "Annie") { throw IOException("offline") }
        compose.waitUntil(5_000) { fetches > 0 }
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithTag(AVATAR_PHOTO_TAG).assertDoesNotExist()
    }
}
