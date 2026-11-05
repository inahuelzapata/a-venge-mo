package com.splitparty.android.domain.receipt

/** One recognised text line and its box in image pixels (ML Kit `Text.Line`). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** The total found on a receipt: the amount in cents and the label line it came from. */
data class ReceiptTotal(val cents: Long, val line: String)

/**
 * Finds the line labelled total on a receipt, never its subtotal, tax or a tip
 * line, and reads its amount: on the same line ("TOTAL $48.50"), or on the
 * same row to the right, as OCR often splits a label and its amount into two
 * lines. Amounts need two decimals so dates, quantities and table numbers never
 * match. Pure integer arithmetic.
 */
object ReceiptTotalParser {
    private val AMOUNT = Regex("""(?<![\d.,])\$?\s?(\d{1,3}(?:,\d{3})+|\d{1,6})[.,](\d{2})(?![\d])""")

    /** Lines that mention a total but are not the receipt's total. */
    private val EXCLUDED = Regex(
        """sub\s*-?\s*total|\btax|\btip|gratuity|suggest|savings|discount|\bitems?\b|tender|change\b|cash\b""",
        RegexOption.IGNORE_CASE
    )

    /** Strongest label first; "total" alone is the weakest (it also appears in other phrases). */
    private val LABELS = listOf(
        Regex("""grand\s*total""", RegexOption.IGNORE_CASE),
        Regex("""total\s*(due|amount)|amount\s*due|balance\s*due""", RegexOption.IGNORE_CASE),
        Regex("""\btotal\b""", RegexOption.IGNORE_CASE)
    )

    private val SUBTOTAL = Regex("""sub\s*-?\s*total""", RegexOption.IGNORE_CASE)

    fun find(lines: List<OcrLine>): ReceiptTotal? {
        val subtotal = lines.filter { SUBTOTAL.containsMatchIn(it.text) }.firstNotNullOfOrNull { amountFor(it, lines) }
        for (label in LABELS) {
            val candidates = lines
                .filter { label.containsMatchIn(it.text) && !EXCLUDED.containsMatchIn(it.text) }
                .mapNotNull { line -> amountFor(line, lines)?.let { ReceiptTotal(it, line.text.trim()) } }
                // A total below the subtotal is a misread (or a different figure); never pick it.
                .filter { it.cents > 0 && (subtotal == null || it.cents >= subtotal) }
            // Several "TOTAL" lines (e.g. a repeated footer): the largest is the bill.
            candidates.maxByOrNull { it.cents }?.let { return it }
        }
        return null
    }

    /** The amount on [label]'s own line, else on the nearest line of the same row to its right. */
    private fun amountFor(label: OcrLine, lines: List<OcrLine>): Long? {
        lastAmount(label.text)?.let { return it }
        return lines
            .filter { it !== label && it.left >= label.left && sameRow(label, it) }
            .sortedBy { kotlin.math.abs(it.centerY - label.centerY) }
            .firstNotNullOfOrNull { lastAmount(it.text) }
    }

    /** Same row: centres closer than three quarters of the taller line's height. */
    private fun sameRow(a: OcrLine, b: OcrLine): Boolean =
        kotlin.math.abs(a.centerY - b.centerY) * ROW_DENOMINATOR <= maxOf(a.height, b.height) * ROW_NUMERATOR

    /** The last amount on a line, in cents ("1,234.56" -> 123456; "48,50" -> 4850). */
    fun lastAmount(text: String): Long? = AMOUNT.findAll(text).lastOrNull()?.let { match ->
        val whole = match.groupValues[1].replace(",", "").toLongOrNull()
        val cents = match.groupValues[2].toLongOrNull()
        if (whole == null || cents == null) null else whole * CENTS_PER_DOLLAR + cents
    }

    private const val CENTS_PER_DOLLAR = 100L
    private const val ROW_NUMERATOR = 3
    private const val ROW_DENOMINATOR = 4
}
