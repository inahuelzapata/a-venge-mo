package com.splitparty.android.ui

import com.splitparty.android.data.LiveEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/** What a live screen section shows. */
sealed interface LiveState<out T> {
    data object Loading : LiveState<Nothing>
    data class Ready<T>(val value: T) : LiveState<T>
    data class Unreachable(val message: String) : LiveState<Nothing>
}

private const val SERVER_TIMEOUT_MS = 5_000L
const val OFFLINE_MESSAGE = "You're offline. The feed comes back by itself when the connection returns."
const val UNREACHABLE_MESSAGE = "Can't reach the database right now. Retrying automatically…"

/**
 * Turns listener events into screen state. Firestore never fails a listener
 * for being offline: it serves the cache with `fromCache = true`. So:
 * - no network at all → Unreachable at once;
 * - data confirmed by the server → Ready;
 * - nothing from the server for 5 s (unreachable emulator, flaky Wi-Fi) → Unreachable.
 * Firestore reconnects by itself, and the next server snapshot brings Ready back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<LiveEvent<T>>.toLiveState(isOnline: Flow<Boolean>): Flow<LiveState<T>> = flow {
    emit(LiveState.Loading)
    emitAll(
        combine(this@toLiveState, isOnline) { event, online -> event to online }
            .transformLatest { (event, online) ->
                when {
                    !online -> emit(LiveState.Unreachable(OFFLINE_MESSAGE))
                    event is LiveEvent.Failed -> emit(LiveState.Unreachable(UNREACHABLE_MESSAGE))
                    event is LiveEvent.Data && !event.fromCache -> emit(LiveState.Ready(event.value))
                    else -> {
                        // Pending, or cache-only data: keep what is on screen, give the server a moment.
                        delay(SERVER_TIMEOUT_MS)
                        emit(LiveState.Unreachable(UNREACHABLE_MESSAGE))
                    }
                }
            },
    )
}
