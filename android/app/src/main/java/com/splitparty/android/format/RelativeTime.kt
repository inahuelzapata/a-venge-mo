package com.splitparty.android.format

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Feed timestamps in the reference screenshot's style: "12 hours ago". */
object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY

    fun format(thenMillis: Long?, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.US): String {
        if (thenMillis == null) return "just now"
        val diff = nowMillis - thenMillis
        return when {
            diff < MINUTE -> "just now"

            // includes small clock skew into the future
            diff < HOUR -> plural(diff / MINUTE, "minute")

            diff < DAY -> plural(diff / HOUR, "hour")

            diff < WEEK -> plural(diff / DAY, "day")

            else -> {
                val sameYear = year(thenMillis, timeZone) == year(nowMillis, timeZone)
                SimpleDateFormat(if (sameYear) "MMM d" else "MMM d, yyyy", locale)
                    .apply { this.timeZone = timeZone }
                    .format(Date(thenMillis))
            }
        }
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"

    private fun year(millis: Long, tz: TimeZone) = Calendar.getInstance(tz).apply { timeInMillis = millis }.get(Calendar.YEAR)
}
