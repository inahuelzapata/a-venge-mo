package com.splitparty.android.data

import com.google.firebase.FirebaseException
import com.google.firebase.functions.FirebaseFunctions
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoResetResult
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Calls `resetDemo` with no arguments (backend/functions/index.js) and expects `{ ok: true, ... }`. */
class FirebaseDemoResetRepository(private val functions: FirebaseFunctions) : DemoResetRepository {
    override suspend fun reset(): DemoResetResult = try {
        val data = withTimeoutOrNull(TIMEOUT_MS) {
            functions.getHttpsCallable(FUNCTION_NAME).call().await().getData()
        }
        if ((data as? Map<*, *>)?.get("ok") == true) DemoResetResult.Done else DemoResetResult.Failed
    } catch (_: FirebaseException) {
        DemoResetResult.Failed
    } catch (_: IllegalStateException) {
        DemoResetResult.Failed
    }

    private companion object {
        const val FUNCTION_NAME = "resetDemo"

        /** The function itself times out at 60 s; a presenter should not wait that long to see an error. */
        const val TIMEOUT_MS = 30_000L
    }
}
