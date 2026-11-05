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

    /** TalkBack form: "39 dollars and 34 cents", "1 dollar", "5 cents". */
    fun spoken(cents: Long): String {
        val abs = if (cents < 0) -(cents.coerceAtLeast(Long.MIN_VALUE + 1)) else cents
        val dollars = abs / CENTS
        val rest = abs % CENTS
        val dollarPart = if (dollars == 1L) "1 dollar" else "$dollars dollars"
        val centPart = if (rest == 1L) "1 cent" else "$rest cents"
        val text = when {
            dollars == 0L -> centPart
            rest == 0L -> dollarPart
            else -> "$dollarPart and $centPart"
        }
        return if (cents < 0) "minus $text" else text
    }

    private const val CENTS = 100L
}
