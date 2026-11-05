package com.splitparty.android.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.model.AvatarCrop
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarImageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val url = "https://taller.ai/_next/image?url=%2Fleadership%2Fmaximiliano-armesto.jpg&w=1080&q=75"
    private val crop = AvatarCrop(0.50f, 0.40f, 0.70f)

    /** A 1080×720 JPEG like the taller.ai landscape portraits. */
    private val jpeg: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private class CountingFetcher(private val respond: () -> ByteArray) : AvatarFetcher {
        var calls = 0
        override fun fetch(url: String): ByteArray {
            calls++
            return respond()
        }
    }

    @Test
    fun aPhotoIsFetchedOnceThenServedFromDiskWithoutTheNetwork() = runTest {
        val online = CountingFetcher { jpeg }
        val first = AvatarImageStore(folder.root, online, UnconfinedTestDispatcher(testScheduler))
        val photo = first.load(url, crop)
        assertEquals(AvatarImageStore.PIXEL_SIZE, photo?.width)
        assertEquals(AvatarImageStore.PIXEL_SIZE, photo?.height)
        first.load(url, crop)
        assertEquals(1, online.calls)
        assertNotNull(first.cached(url, crop))

        // A new run (empty memory) in Airplane Mode still has it.
        val offline = CountingFetcher { throw IOException("offline") }
        val relaunched = AvatarImageStore(folder.root, offline, UnconfinedTestDispatcher(testScheduler))
        assertNull(relaunched.cached(url, crop))
        val fromDisk = relaunched.load(url, crop)
        assertEquals(AvatarImageStore.PIXEL_SIZE, fromDisk?.width)
        assertEquals(0, offline.calls)
    }

    @Test
    fun aFailedFetchShowsNothingAndIsRetriedNextTime() = runTest {
        var online = false
        val fetcher = CountingFetcher { if (online) jpeg else throw IOException("offline") }
        val store = AvatarImageStore(folder.root, fetcher, UnconfinedTestDispatcher(testScheduler))

        assertNull(store.load(url, crop))
        online = true
        assertNotNull(store.load(url, crop))
        assertEquals(2, fetcher.calls)
    }

    @Test
    fun somethingThatIsNotAnImageShowsNothing() = runTest {
        val store = AvatarImageStore(folder.root, { "<html>".toByteArray() }, UnconfinedTestDispatcher(testScheduler))
        assertNull(store.load(url, crop))
    }

    @Test
    fun cropsAreSquaresKeptInsideTheImage() {
        // Maxi, 1080×720: a 504 square around (540, 288).
        assertEquals(Triple(288, 36, 504), AvatarImageStore.cropRect(1080, 720, crop))
        // Christophe, 667×1000: the full width, starting just below the top.
        assertEquals(Triple(0, 16, 667), AvatarImageStore.cropRect(667, 1000, AvatarCrop(0.50f, 0.35f, 1.00f)))
        // A centre near the edge is pushed back inside.
        assertEquals(Triple(0, 0, 100), AvatarImageStore.cropRect(200, 100, AvatarCrop(0f, 0f, 1f)))
    }
}
