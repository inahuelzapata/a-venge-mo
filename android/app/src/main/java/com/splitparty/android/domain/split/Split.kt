package com.splitparty.android.domain.split

/**
 * `parties.tip` (docs/firestore-schema.md): exactly one of the two shapes.
 * A 0% tip is stored as `Custom(0)`, because the rules only accept the percent
 * presets 15/18/20/25 (chiron-memory decisions: "A 0% tip is stored as ...").
 */
sealed interface Tip {
    data class Percent(val percent: Int) : Tip

    data class Custom(val amountCents: Long) : Tip

    companion object {
        /** The rules' percent presets (backend/lib/schema.js TIP_PERCENT_PRESETS). */
        val PERCENT_PRESETS = listOf(15, 18, 20, 25)

        val ZERO: Tip = Custom(0L)
    }
}

/** Shares in `memberIds` order (host first). */
data class SplitResult(val tipCents: Long, val totalCents: Long, val shares: List<Long>) {
    val headcount: Int get() = shares.size
}

/**
 * Port of backend/lib/split.js, integer cents only. Percent tips round half up;
 * share = floor(total / headcount); the leftover cents go to the host (first
 * member), so the shares sum exactly to the total. A null tip counts as 0%.
 */
object Split {
    fun tipCents(billCents: Long, tip: Tip?): Long {
        require(billCents >= 0) { "billCents must be >= 0" }
        return when (tip) {
            null -> 0L

            is Tip.Custom -> {
                require(tip.amountCents >= 0) { "tip.amountCents must be >= 0" }
                tip.amountCents
            }

            is Tip.Percent -> {
                require(tip.percent >= 0) { "tip.percent must be >= 0" }
                Math.floorDiv(billCents * tip.percent + HALF_CENT_ROUNDING, PERCENT)
            }
        }
    }

    fun compute(billCents: Long, tip: Tip?, headcount: Int): SplitResult {
        require(headcount > 0) { "headcount must be > 0" }
        val tipValue = tipCents(billCents, tip)
        val total = billCents + tipValue
        val base = Math.floorDiv(total, headcount.toLong())
        val leftover = total - base * headcount
        return SplitResult(tipValue, total, List(headcount) { i -> if (i == 0) base + leftover else base })
    }

    private const val HALF_CENT_ROUNDING = 50L
    private const val PERCENT = 100L
}
