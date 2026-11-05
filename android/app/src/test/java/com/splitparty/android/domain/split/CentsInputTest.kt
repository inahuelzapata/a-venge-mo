package com.splitparty.android.domain.split

import org.junit.Assert.assertEquals
import org.junit.Test

class CentsInputTest {
    private fun valid(text: String) = (CentsInput.parse(text) as AmountParse.Valid).cents

    private fun error(text: String) = (CentsInput.parse(text) as AmountParse.Rejected).error

    @Test
    fun `accepts the range bounds and common forms`() {
        assertEquals(1L, valid("0.01"))
        assertEquals(999_999L, valid("9999.99"))
        assertEquals(4_850L, valid("48.50"))
        assertEquals(4_850L, valid("48.5"))
        assertEquals(4_800L, valid("48"))
        assertEquals(4_800L, valid("48."))
        assertEquals(50L, valid(".5"))
        assertEquals(123_456L, valid("$1,234.56"))
    }

    @Test
    fun `blocks zero, too big, three decimals and empty`() {
        assertEquals(AmountError.BelowMinimum, error("0"))
        assertEquals(AmountError.BelowMinimum, error("0.00"))
        assertEquals(AmountError.AboveMaximum, error("10000.00"))
        assertEquals(AmountError.AboveMaximum, error("99999999999999999999"))
        assertEquals(AmountError.TooManyDecimals, error("12.345"))
        assertEquals(AmountError.Empty, error(""))
        assertEquals(AmountError.Invalid, error("."))
        assertEquals(AmountError.Invalid, error("1.2.3"))
        assertEquals(AmountError.Invalid, error("-5"))
    }

    @Test
    fun `custom tip may be zero when min is zero`() {
        assertEquals(AmountParse.Valid(0L), CentsInput.parse("0", min = 0))
    }

    @Test
    fun `round trips to keypad text`() {
        assertEquals("48.50", CentsInput.toInput(4_850))
        assertEquals("0.01", CentsInput.toInput(1))
    }
}
