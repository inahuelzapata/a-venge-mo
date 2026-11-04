package com.splitparty.android.format

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_800_000_000_000L // 2027-01-15 UTC
    private val utc = TimeZone.getTimeZone("UTC")

    private fun ago(millis: Long) = RelativeTime.format(now - millis, now, utc)

    @Test
    fun `recent and slightly future times read Just now, as on iOS`() {
        assertEquals("Just now", ago(10_000))
        assertEquals("Just now", ago(-5_000))
        assertEquals("Just now", RelativeTime.format(null, now, utc))
    }

    @Test
    fun `minutes hours and days use singular and plural`() {
        assertEquals("1 minute ago", ago(60_000))
        assertEquals("12 hours ago", ago(12 * 3_600_000L))
        assertEquals("1 day ago", ago(86_400_000L))
        assertEquals("3 days ago", ago(3 * 86_400_000L))
    }

    @Test
    fun `older rows use weeks, months and years like the iOS formatter`() {
        assertEquals("1 week ago", ago(10 * 86_400_000L))
        assertEquals("2 weeks ago", ago(20 * 86_400_000L))
        assertEquals("1 month ago", ago(40 * 86_400_000L))
        assertEquals("1 year ago", ago(400 * 86_400_000L))
    }
}
