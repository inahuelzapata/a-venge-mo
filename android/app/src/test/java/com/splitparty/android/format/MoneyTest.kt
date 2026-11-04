package com.splitparty.android.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun `formats integer cents with thousands separators`() {
        assertEquals("$0.00", Money.format(0))
        assertEquals("$0.05", Money.format(5))
        assertEquals("$31.15", Money.format(3115))
        assertEquals("$250.00", Money.format(25000))
        assertEquals("$1,234,567.89", Money.format(123456789))
    }

    @Test
    fun `formats negative amounts with a leading minus`() {
        assertEquals("-$12.30", Money.format(-1230))
    }

    @Test
    fun `Long MIN_VALUE does not overflow`() {
        assertEquals("-$92,233,720,368,547,758.07", Money.format(Long.MIN_VALUE))
    }
}
