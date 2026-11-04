package com.splitparty.android.format

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/**
 * Feed timestamps worded like the iOS app (RelativeDateTimeFormatter, full
 * style, English): "Just now", "12 hours ago", "2 weeks ago", "3 months ago".
 * The largest whole calendar unit wins, so both platforms print the same text.
 */
object RelativeTime {
    private const val MINUTE = 60_000L
    private const val DAYS_PER_WEEK = 7

    fun format(thenMillis: Long?, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        // No server time yet means it just happened; also covers small clock skew into the future.
        if (thenMillis == null || nowMillis - thenMillis < MINUTE) return "Just now"
        val zone = timeZone.toZoneId()
        val then = Instant.ofEpochMilli(thenMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val days = ChronoUnit.DAYS.between(then, now)
        return when {
            ChronoUnit.YEARS.between(then, now) > 0 -> plural(ChronoUnit.YEARS.between(then, now), "year")
            ChronoUnit.MONTHS.between(then, now) > 0 -> plural(ChronoUnit.MONTHS.between(then, now), "month")
            days >= DAYS_PER_WEEK -> plural(days / DAYS_PER_WEEK, "week")
            days > 0 -> plural(days, "day")
            ChronoUnit.HOURS.between(then, now) > 0 -> plural(ChronoUnit.HOURS.between(then, now), "hour")
            else -> plural(ChronoUnit.MINUTES.between(then, now), "minute")
        }
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
}
