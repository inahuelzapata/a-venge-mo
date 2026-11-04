package com.splitparty.android.domain.model

/** One emission of a live database listener. */
sealed interface LiveEvent<out T> {
    /** Listener (re)started, nothing received yet. */
    data object Pending : LiveEvent<Nothing>

    /** [fromCache] = true means the server has not confirmed this data (offline or unreachable). */
    data class Data<T>(val value: T, val fromCache: Boolean) : LiveEvent<T>

    /** The listener failed; it is retried automatically. */
    data class Failed(val error: Throwable) : LiveEvent<Nothing>
}
