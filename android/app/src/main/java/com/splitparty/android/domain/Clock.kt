package com.splitparty.android.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Wall-clock epoch millis. Party expiry is judged by it on every phone. Tests drive a virtual one. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

/**
 * The clock now, then again at each of [deadlines] still ahead (epoch
 * millis, any order), so a party flips to expired the moment its expiry
 * passes. Completes after the last deadline: no timer runs when nothing is due.
 */
fun Clock.at(deadlines: Collection<Long>): Flow<Long> = flow {
    emit(now())
    for (deadline in deadlines.toSortedSet()) {
        val wait = deadline - now()
        if (wait <= 0) continue
        delay(wait)
        emit(now())
    }
}

/**
 * The clock every [periodMs] until [until] has passed (a countdown), then once
 * more at [until]. Completes afterwards.
 */
fun Clock.countdown(until: Long, periodMs: Long = 1_000L): Flow<Long> = flow {
    var current = now()
    emit(current)
    while (current < until) {
        delay(minOf(periodMs - current.mod(periodMs), until - current).coerceAtLeast(1L))
        current = now()
        emit(current)
    }
}
