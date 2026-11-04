package com.splitparty.android.format

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_800_000_000_000L // 2027-01-15 UTC
    private val utc = TimeZone.getTimeZone("UTC")

    private fun ago(millis: Long) = RelativeTime.format(now - millis, now, utc)

    @Test
    fun `recent and slightly future times read just now`() {
        assertEquals("just now", ago(10_000))
        assertEquals("just now", ago(-5_000))
        assertEquals("just now", RelativeTime.format(null, now, utc))
    }

    @Test
    fun `minutes hours and days use singular and plural`() {
        assertEquals("1 minute ago", ago(60_000))
        assertEquals("12 hours ago", ago(12 * 3_600_000L))
        assertEquals("1 day ago", ago(86_400_000L))
        assertEquals("3 days ago", ago(3 * 86_400_000L))
    }

    @Test
    fun `older than a week shows the date, with the year only when it differs`() {
        assertEquals("Jan 5", ago(10 * 86_400_000L))
        assertEquals("Dec 26, 2026", ago(20 * 86_400_000L))
    }
}
