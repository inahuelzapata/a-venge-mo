package com.splitparty.android.format

/**
 * Display-only formatting of integer cents. Pure integer arithmetic: money is
 * never converted to Float/Double anywhere in the app (docs/firestore-schema.md).
 */
object Money {
    fun format(cents: Long): String {
        val negative = cents < 0
        // Long.MIN_VALUE has no positive counterpart; it is far outside any real amount.
        val abs = if (negative) -(cents.coerceAtLeast(Long.MIN_VALUE + 1)) else cents
        val dollars = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val rest = (abs % 100).toString().padStart(2, '0')
        return (if (negative) "-$" else "$") + dollars + "." + rest
    }
}
