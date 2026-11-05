package com.splitparty.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.splitparty.android.domain.model.AvatarCrop
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Downloads the bytes of an avatar photo. Throws on any failure. */
fun interface AvatarFetcher {
    fun fetch(url: String): ByteArray
}

/** Plain HTTPS GET with short timeouts: the bubble shows initials meanwhile. */
object HttpAvatarFetcher : AvatarFetcher {
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 5_000_000
    private val SUCCESS = 200..299

    override fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in SUCCESS) throw IOException("HTTP ${connection.responseCode} for $url")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) throw IOException("Unexpected photo size ${bytes.size} for $url")
            return bytes
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Avatar photos, fetched once and kept on the phone (docs/design/visual-conventions.md).
 *
 * Each photo is cropped to its square and scaled to [PIXEL_SIZE] once, at download
 * time, then written to [directory] (app files, so the system never evicts it).
 * After that it is read from disk, and from memory within a run, with no network
 * round-trip: the demo works in Airplane Mode once each photo has loaded once.
 * Failures are not remembered; the next [load] tries again.
 */
class AvatarImageStore(
    private val directory: File,
    private val fetcher: AvatarFetcher = HttpAvatarFetcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The photo if it is already in memory; never touches disk or network. */
    fun cached(url: String, crop: AvatarCrop): Bitmap? = memory.get(cacheKey(url, crop))

    /** The photo from memory, disk or the network, in that order; null when it can't be shown. */
    suspend fun load(url: String, crop: AvatarCrop): Bitmap? {
        val key = cacheKey(url, crop)
        memory.get(key)?.let { return it }
        return locks.getOrPut(key) { Mutex() }.withLock {
            memory.get(key) ?: withContext(ioDispatcher) { fromDisk(key) ?: fromNetwork(key, url, crop) }
                ?.also { memory.put(key, it) }
        }
    }

    private fun fromDisk(key: String): Bitmap? {
        val file = File(directory, "$key.jpg")
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) file.delete()
        return bitmap
    }

    private fun fromNetwork(key: String, url: String, crop: AvatarCrop): Bitmap? {
        val square = runCatching { squareBitmap(fetcher.fetch(url), crop) }.getOrNull() // Any failure: initials.
        if (square != null) {
            // A failed write still shows the photo this run; the next run fetches again.
            runCatching {
                directory.mkdirs()
                val tmp = File(directory, "$key.tmp")
                tmp.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                tmp.renameTo(File(directory, "$key.jpg"))
            }
        }
        return square
    }

    companion object {
        /** The largest bubble is 80 dp (Me header); 4x covers xxxhdpi screens. */
        const val PIXEL_SIZE = 320
        private const val JPEG_QUALITY = 90
        private const val MEMORY_ENTRIES = 32
        private const val MIN_CROP_SIDE = 0.01f
        private const val KEY_HEX_CHARS = 32

        /** Crop + scale are part of the key, so a new crop replaces the old file. */
        internal fun cacheKey(url: String, crop: AvatarCrop): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$url|${crop.centerX}|${crop.centerY}|${crop.side}|$PIXEL_SIZE".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(KEY_HEX_CHARS)
        }

        /** Decodes [bytes], cuts out [crop] and scales it to [PIXEL_SIZE] square. */
        internal fun squareBitmap(bytes: ByteArray, crop: AvatarCrop): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val source = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, crop) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else {
                null
            }
            return source?.let {
                val (left, top, side) = cropRect(it.width, it.height, crop)
                Bitmap.createScaledBitmap(Bitmap.createBitmap(it, left, top, side, side), PIXEL_SIZE, PIXEL_SIZE, true)
            }
        }

        /** Decodes at no less than twice the target, to keep memory low without losing sharpness. */
        private fun sampleSize(width: Int, height: Int, crop: AvatarCrop): Int {
            val cropEdge = minOf(width, height) * crop.side
            var sample = 1
            while (cropEdge / (sample * 2) >= PIXEL_SIZE * 2) sample *= 2
            return sample
        }

        /** The crop's square in pixels (left, top, side), kept inside the image. */
        internal fun cropRect(width: Int, height: Int, crop: AvatarCrop): Triple<Int, Int, Int> {
            val side = (minOf(width, height) * crop.side.coerceIn(MIN_CROP_SIDE, 1f)).toInt().coerceAtLeast(1)
            val left = (width * crop.centerX - side / 2f).toInt().coerceIn(0, width - side)
            val top = (height * crop.centerY - side / 2f).toInt().coerceIn(0, height - side)
            return Triple(left, top, side)
        }
    }
}
