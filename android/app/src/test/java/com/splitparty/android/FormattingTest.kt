package com.splitparty.android

import com.splitparty.android.data.FirebaseSetup
import com.splitparty.android.data.Username
import com.splitparty.android.format.Money
import com.splitparty.android.format.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class FormattingTest {
    @Test fun moneyFormatsIntegerCents() {
        assertEquals("$0.00", Money.format(0))
        assertEquals("$0.05", Money.format(5))
        assertEquals("$31.15", Money.format(3115))
        assertEquals("$250.00", Money.format(25000))
        assertEquals("$180.50", Money.format(18050))
        assertEquals("$320.75", Money.format(32075))
        assertEquals("$1,234,567.89", Money.format(123456789))
        assertEquals("-$12.30", Money.format(-1230))
    }

    @Test fun usernameNormalizesCaseAndSpaces() {
        assertEquals("maxi", Username.normalize("  MaXi "))
        assertEquals("christophe", Username.normalize("Christophe"))
        assertTrue(Username.isValid("annie"))
        assertFalse(Username.isValid(""))
        assertFalse(Username.isValid("a/b"))
        assertFalse(Username.isValid("ab"))
    }

    @Test fun relativeTimeMatchesScreenshotStyle() {
        val now = 1_800_000_000_000L
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("just now", RelativeTime.format(now - 10_000, now, utc))
        assertEquals("just now", RelativeTime.format(now + 5_000, now, utc))
        assertEquals("1 minute ago", RelativeTime.format(now - 60_000, now, utc))
        assertEquals("12 hours ago", RelativeTime.format(now - 12 * 3_600_000L, now, utc))
        assertEquals("3 days ago", RelativeTime.format(now - 3 * 86_400_000L, now, utc))
        assertEquals("Jan 5", RelativeTime.format(now - 10 * 86_400_000L, now, utc))
    }

    @Test fun emulatorHostParsing() {
        assertEquals("192.168.1.20" to 8080, FirebaseSetup.parseHostPort("192.168.1.20:8080"))
        assertNull(FirebaseSetup.parseHostPort("192.168.1.20"))
        assertNull(FirebaseSetup.parseHostPort(":8080"))
    }
}
