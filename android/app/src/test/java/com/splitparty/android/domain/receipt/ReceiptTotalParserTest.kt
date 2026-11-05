package com.splitparty.android.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTotalParserTest {
    /** A receipt laid out as rows; each pair is (label, amount) recognised as two lines on one row. */
    private fun receipt(vararg rows: Pair<String, String?>): List<OcrLine> = rows.flatMapIndexed { i, (label, amount) ->
        val top = 100 + i * 40
        listOfNotNull(
            OcrLine(label, 20, top, 300, top + 30),
            amount?.let { OcrLine(it, 600, top + 3, 760, top + 33) }
        )
    }

    private fun one(vararg texts: String) = texts.mapIndexed { i, t -> OcrLine(t, 20, 100 + i * 40, 760, 130 + i * 40) }

    @Test
    fun `picks the total row, not subtotal or tax`() {
        val lines = receipt(
            "TONY'S" to null,
            "Greenwich Ave, CT" to null,
            "2 Margherita" to "38.00",
            "1 Caesar" to "12.50",
            "Subtotal" to "50.50",
            "Tax 6.35%" to "3.21",
            "Total" to "53.71",
            "Thank you!" to null
        )
        assertEquals(ReceiptTotal(5_371, "Total"), ReceiptTotalParser.find(lines))
    }

    @Test
    fun `reads the amount on the same line`() {
        assertEquals(4_850L, ReceiptTotalParser.find(one("SUBTOTAL $45.00", "TAX $3.50", "TOTAL $48.50"))?.cents)
    }

    @Test
    fun `ignores suggested tip lines that mention a total`() {
        val lines = one(
            "Subtotal 100.00",
            "Total 106.35",
            "Suggested tip: 18% = 18.00, total 124.35",
            "Tip ____ Total ____"
        )
        assertEquals(10_635L, ReceiptTotalParser.find(lines)?.cents)
    }

    @Test
    fun `prefers amount due and grand total over a bare total`() {
        assertEquals(6_000L, ReceiptTotalParser.find(one("Total 55.00", "Amount due 60.00"))?.cents)
        assertEquals(7_000L, ReceiptTotalParser.find(one("Total 55.00", "GRAND TOTAL 70.00"))?.cents)
    }

    @Test
    fun `thousands separators and comma decimals`() {
        assertEquals(123_456L, ReceiptTotalParser.lastAmount("TOTAL $1,234.56"))
        assertEquals(4_850L, ReceiptTotalParser.lastAmount("TOTAL 48,50"))
    }

    @Test
    fun `dates, times and quantities are not amounts`() {
        assertNull(ReceiptTotalParser.find(one("11/04/2026 19:42", "Table 12 Guests 3", "Total")))
    }

    @Test
    fun `blank page or no total line finds nothing`() {
        assertNull(ReceiptTotalParser.find(emptyList()))
        assertNull(ReceiptTotalParser.find(one("Margherita 19.00", "Caesar 12.50")))
    }

    @Test
    fun `a total below the subtotal is rejected as a misread`() {
        assertNull(ReceiptTotalParser.find(one("Subtotal 50.50", "Total 5.37")))
    }
}
