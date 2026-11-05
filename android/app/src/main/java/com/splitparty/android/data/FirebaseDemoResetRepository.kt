package com.splitparty.android.data

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.splitparty.android.domain.repository.DemoResetFailure
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoResetResult
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Calls the Demo reset with no arguments and no auth, over the Firebase callable
 * protocol, and expects `{ ok: true, ... }`:
 * - [endpointUrl] blank: the callable [FUNCTION_NAME] on [functions], which
 *   [com.splitparty.android.data.firebase.FirebaseSetup.functions] builds for the
 *   Firestore app's project in us-central1 (backend/functions/index.js; needs Blaze);
 * - else that URL: the free endpoint (backend/reset-endpoint), same protocol.
 * Every failure is logged under [TAG] with its Functions code and message.
 */
class FirebaseDemoResetRepository(
    private val functions: FirebaseFunctions,
    private val endpointUrl: String = "",
    private val timeoutMs: Long = TIMEOUT_MS
) : DemoResetRepository {
    private fun callable() = if (endpointUrl.isBlank()) {
        functions.getHttpsCallable(FUNCTION_NAME)
    } else {
        functions.getHttpsCallableFromUrl(URL(endpointUrl.trim()))
    }

    override suspend fun reset(): DemoResetResult = try {
        val answer = withTimeoutOrNull(timeoutMs) {
            Answer(callable().call().await().getData())
        }
        when {
            answer == null -> failed(DemoResetFailure.Unreachable, "timed out after $timeoutMs ms")
            (answer.data as? Map<*, *>)?.get("ok") == true -> DemoResetResult.Done
            else -> failed(DemoResetFailure.Server, "unexpected answer ${answer.data}")
        }
    } catch (e: MalformedURLException) {
        Log.w(TAG, "RESET_DEMO_URL \"$endpointUrl\" is not a URL", e)
        DemoResetResult.Failed(DemoResetFailure.Server, "RESET_DEMO_URL in firestore.properties is not a valid URL.")
    } catch (e: FirebaseFunctionsException) {
        Log.w(TAG, "$FUNCTION_NAME failed: ${e.code.name}: ${e.message}", e)
        classifyResetError(e.code, e.message, networkFailure = e.cause is IOException)
    } catch (e: FirebaseException) {
        Log.w(TAG, "$FUNCTION_NAME failed: ${e.message}", e)
        DemoResetResult.Failed(DemoResetFailure.Unreachable)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "$FUNCTION_NAME failed: ${e.message}", e)
        DemoResetResult.Failed(DemoResetFailure.Server)
    } catch (e: IllegalArgumentException) {
        // e.g. the SDK rejecting the app's Firebase options before any request.
        Log.w(TAG, "$FUNCTION_NAME failed: ${e.message}", e)
        DemoResetResult.Failed(DemoResetFailure.Server)
    }

    private fun failed(reason: DemoResetFailure, detail: String): DemoResetResult {
        Log.w(TAG, "$FUNCTION_NAME failed: $detail")
        return DemoResetResult.Failed(reason)
    }

    /** Wraps the data so a null result is not mistaken for a timeout. */
    private class Answer(val data: Any?)

    companion object {
        /** backend/lib/deploy.js RESET_DEMO_FUNCTION; DemoResetContractTest keeps them equal. */
        const val FUNCTION_NAME = "resetDemo"

        const val TAG = "DemoReset"

        /** The function itself times out at 60 s; a presenter should not wait that long to see an error. */
        const val TIMEOUT_MS = 30_000L
    }
}

/**
 * Maps a callable error to what the phone says. The SDK reports a dropped
 * connection as INTERNAL caused by an IOException, and a missing function as
 * NOT_FOUND; when the function threw an HttpsError its own text is the
 * message, otherwise the message is just the code's name.
 */
internal fun classifyResetError(code: FirebaseFunctionsException.Code, message: String?, networkFailure: Boolean): DemoResetResult.Failed {
    val ownMessage = message?.trim()?.takeIf { it.isNotEmpty() && it != code.name }
    return when {
        networkFailure -> DemoResetResult.Failed(DemoResetFailure.Unreachable)

        code == FirebaseFunctionsException.Code.NOT_FOUND -> DemoResetResult.Failed(DemoResetFailure.NotDeployed)

        code == FirebaseFunctionsException.Code.UNAVAILABLE ||
            code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> DemoResetResult.Failed(DemoResetFailure.Unreachable)

        else -> DemoResetResult.Failed(DemoResetFailure.Server, ownMessage)
    }
}
