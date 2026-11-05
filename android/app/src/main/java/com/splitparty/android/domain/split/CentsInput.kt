package com.splitparty.android.domain.split

/** Why a typed amount is not accepted; the screen maps each to an inline message. */
enum class AmountError { Empty, TooManyDecimals, Invalid, BelowMinimum, AboveMaximum }

sealed interface AmountParse {
    data class Valid(val cents: Long) : AmountParse

    data class Rejected(val error: AmountError) : AmountParse
}

/**
 * Parses "48.50", "48.5", "48", "1,234.56" into integer cents with string
 * arithmetic only (never Float/Double). Bills and custom tips accept
 * [min]..[max] cents: by default 0.01 to 9,999.99.
 */
object CentsInput {
    const val MIN_CENTS = 1L
    const val MAX_CENTS = 999_999L
    private const val MAX_DECIMALS = 2
    private const val MAX_INTEGER_DIGITS = 9

    fun parse(text: String, min: Long = MIN_CENTS, max: Long = MAX_CENTS): AmountParse {
        val cleaned = text.trim().removePrefix("$").replace(",", "")
        if (cleaned.isEmpty()) return AmountParse.Rejected(AmountError.Empty)
        val parts = cleaned.split('.')
        val whole = parts[0]
        val fraction = parts.getOrNull(1).orEmpty()
        return when {
            parts.size > 2 || (whole.isEmpty() && fraction.isEmpty()) -> AmountParse.Rejected(AmountError.Invalid)

            !whole.all(Char::isDigit) || !fraction.all(Char::isDigit) -> AmountParse.Rejected(AmountError.Invalid)

            fraction.length > MAX_DECIMALS -> AmountParse.Rejected(AmountError.TooManyDecimals)

            whole.trimStart('0').length > MAX_INTEGER_DIGITS -> AmountParse.Rejected(AmountError.AboveMaximum)

            else -> {
                val cents = (whole.ifEmpty { "0" }.toLong() * CENTS_PER_DOLLAR) + fraction.padEnd(MAX_DECIMALS, '0').toLong()
                when {
                    cents < min -> AmountParse.Rejected(AmountError.BelowMinimum)
                    cents > max -> AmountParse.Rejected(AmountError.AboveMaximum)
                    else -> AmountParse.Valid(cents)
                }
            }
        }
    }

    /** Cents back to the keypad's text form ("48.50"), for editing a pre-filled amount. */
    fun toInput(cents: Long): String = "${cents / CENTS_PER_DOLLAR}.${(cents % CENTS_PER_DOLLAR).toString().padStart(MAX_DECIMALS, '0')}"

    private const val CENTS_PER_DOLLAR = 100L
}
