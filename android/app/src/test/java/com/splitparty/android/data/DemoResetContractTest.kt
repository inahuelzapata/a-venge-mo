package com.splitparty.android.data

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.splitparty.android.data.firebase.FirebaseSetup
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.domain.repository.DemoResetFailure
import com.splitparty.android.domain.repository.DemoResetResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The seam with backend/functions/index.js: the Demo reset calls the callable
 * `resetDemo` in us-central1, in the Firestore app's project, with no
 * arguments and no auth, and reads `{ ok: true }`. The real Functions SDK
 * talks to a local socket standing in for the Functions emulator, so the
 * request it sends is checked byte for byte.
 */
@RunWith(AndroidJUnit4::class)
class DemoResetContractTest {
    private lateinit var app: FirebaseApp
    private lateinit var server: ServerSocket

    @Before
    fun setUp() {
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("AIza" + "A".repeat(35)) // the SDK checks the key's shape
            .setProjectId(PROJECT)
            .build()
        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "contract-${System.nanoTime()}")
        server = ServerSocket(0)
    }

    @After
    fun tearDown() {
        server.close()
        app.delete()
    }

    @Test
    fun nameAndRegionMatchTheBackend() {
        val deploy = File("../../backend/lib/deploy.js").readText()
        val index = File("../../backend/functions/index.js").readText()
        assertTrue(deploy.contains("RESET_DEMO_FUNCTION = '${FirebaseDemoResetRepository.FUNCTION_NAME}'"))
        assertTrue(deploy.contains("RESET_DEMO_REGION = '${FirebaseSetup.FUNCTIONS_REGION}'"))
        assertTrue(index.contains("export const ${FirebaseDemoResetRepository.FUNCTION_NAME} = onCall("))
        assertTrue(index.contains("RESET_DEMO_REGION = '${FirebaseSetup.FUNCTIONS_REGION}'"))
    }

    @Test
    fun callsResetDemoInUsCentral1OfTheSameProjectWithNoArgumentsAndNoAuth() {
        val request = answerOnce(200, """{"result":{"ok":true,"resetAt":"2026-11-05T00:00:00.000Z","removed":{},"seeded":{}}}""")

        assertEquals(DemoResetResult.Done, reset())

        val sent = request.get()
        assertEquals("POST /$PROJECT/us-central1/resetDemo HTTP/1.1", sent.requestLine)
        assertTrue(JSONObject(sent.body).isNull("data"))
        assertFalse(sent.headers.keys.any { it.equals("authorization", ignoreCase = true) })
    }

    @Test
    fun aMissingFunctionIsNotDeployed() {
        answerOnce(404, "<html>Page not found</html>", contentType = "text/html")
        assertEquals(DemoResetResult.Failed(DemoResetFailure.NotDeployed), reset())
    }

    @Test
    fun aPartialResetCarriesTheFunctionsOwnMessage() {
        val partial = "Demo reset did not complete (boom). The database may be partially reset; tap Reset again."
        answerOnce(500, """{"error":{"status":"INTERNAL","message":"$partial"}}""")
        assertEquals(DemoResetResult.Failed(DemoResetFailure.Server, partial), reset())
    }

    @Test
    fun anUnknownServerErrorHasNoMessage() {
        answerOnce(500, """{"error":{"status":"INTERNAL","message":"INTERNAL"}}""")
        assertEquals(DemoResetResult.Failed(DemoResetFailure.Server), reset())
    }

    @Test
    fun nobodyListeningIsUnreachable() {
        server.close() // the port now refuses connections, as with no network
        assertEquals(DemoResetResult.Failed(DemoResetFailure.Unreachable), reset())
    }

    @Test
    fun noAnswerInTimeIsUnreachable() {
        thread(isDaemon = true) { runCatching { server.accept() } } // accepts, never answers
        assertEquals(DemoResetResult.Failed(DemoResetFailure.Unreachable), reset(timeoutMs = 300))
    }

    @Test
    fun withAnEndpointUrlItPostsTheSameCallToThatUrl() {
        val request = answerOnce(200, """{"result":{"ok":true,"resetAt":"2026-11-05T00:00:00.000Z","removed":{},"seeded":{}}}""")

        assertEquals(DemoResetResult.Done, reset(endpointUrl = "http://127.0.0.1:${server.localPort}/api/resetDemo"))

        val sent = request.get()
        assertEquals("POST /api/resetDemo HTTP/1.1", sent.requestLine)
        assertTrue(JSONObject(sent.body).isNull("data"))
        assertFalse(sent.headers.keys.any { it.equals("authorization", ignoreCase = true) })
    }

    @Test
    fun theEndpointsErrorsReadLikeTheCallables() {
        val partial = "Demo reset did not complete (boom). The database may be partially reset; tap Reset again."
        answerOnce(500, """{"error":{"status":"INTERNAL","message":"$partial"}}""")
        val url = "http://127.0.0.1:${server.localPort}/api/resetDemo"
        assertEquals(DemoResetResult.Failed(DemoResetFailure.Server, partial), reset(endpointUrl = url))

        answerOnce(404, "The page could not be found", contentType = "text/plain")
        assertEquals(DemoResetResult.Failed(DemoResetFailure.NotDeployed), reset(endpointUrl = url))
    }

    @Test
    fun aMalformedEndpointUrlIsReportedNotThrown() {
        val result = reset(endpointUrl = "not a url")
        assertEquals(DemoResetFailure.Server, (result as DemoResetResult.Failed).reason)
        assertEquals("RESET_DEMO_URL in firestore.properties is not a valid URL.", result.message)
    }

    private fun reset(timeoutMs: Long = 10_000, endpointUrl: String = ""): DemoResetResult {
        // With an endpoint URL the app is on the hosted target: no Functions emulator involved.
        val target = if (endpointUrl.isEmpty()) FirestoreTarget.Emulator(PROJECT, "127.0.0.1:8080") else FirestoreTarget.Hosted(PROJECT)
        val functions = FirebaseSetup.functions(app, target, server.localPort)
        val repository = FirebaseDemoResetRepository(functions, endpointUrl = endpointUrl, timeoutMs = timeoutMs)
        return runBlocking {
            val call = async(Dispatchers.IO) { repository.reset() }
            // The SDK hops through the main thread; Robolectric's looper only runs when idled.
            while (!call.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            call.await()
        }
    }

    private class Request(val requestLine: String, val headers: Map<String, String>, val body: String)

    private fun answerOnce(status: Int, body: String, contentType: String = "application/json"): java.util.concurrent.Future<Request> {
        val result = java.util.concurrent.CompletableFuture<Request>()
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine()
                val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
                    .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
                val length = headers.entries.firstOrNull { it.key.equals("content-length", true) }?.value?.toInt() ?: 0
                val chars = CharArray(length)
                var read = 0
                while (read < length) read += reader.read(chars, read, length - read).takeIf { it > 0 } ?: break
                result.complete(Request(requestLine, headers, String(chars, 0, read)))
                val bytes = body.toByteArray()
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 $status X\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray()
                    )
                    write(bytes)
                    flush()
                }
            }
        }
        return object : java.util.concurrent.Future<Request> by result {
            override fun get(): Request = result.get(5, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val PROJECT = "demo-reset-contract"
    }
}
