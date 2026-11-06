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

/** Why a party's custom amounts cannot be used with its total (docs/firestore-schema.md, "Custom shares"). */
enum class CustomShareProblem {
    /** The custom amounts add up to more than the total. */
    OverTotal,

    /** Every member has a custom amount, but they add up to less than the total. */
    Unassigned
}

/**
 * Shares in `memberIds` order (host first). [custom]: which members pay a custom
 * amount (the others split the remainder). When [problem] is set the amounts do
 * not fit the total: [shares] then holds the custom amounts and 0 for everyone
 * else, for display only — nothing may be settled.
 */
data class SplitResult(
    val tipCents: Long,
    val totalCents: Long,
    val shares: List<Long>,
    val custom: List<Boolean> = List(shares.size) { false },
    val problem: CustomShareProblem? = null
) {
    val headcount: Int get() = shares.size

    val fits: Boolean get() = problem == null

    /** Sum of the custom amounts. */
    val customCents: Long get() = shares.withIndex().sumOf { (i, share) -> if (custom[i]) share else 0L }
}

/**
 * Port of backend/lib/split.js, integer cents only. Percent tips round half up.
 * Members with a custom amount pay exactly that; the rest of the total is split
 * equally among the others (floor), and the leftover cents go to the first of
 * them in member order (the host if the host is on the equal split, otherwise
 * the first joinee on it), so the shares sum exactly to the total. A null tip
 * counts as 0%.
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

    fun compute(billCents: Long, tip: Tip?, headcount: Int): SplitResult = compute(billCents, tip, List(headcount) { null })

    /** [customShares]: one entry per member in `memberIds` order, null = on the equal split. */
    fun compute(billCents: Long, tip: Tip?, customShares: List<Long?>): SplitResult {
        val headcount = customShares.size
        require(headcount > 0) { "headcount must be > 0" }
        require(customShares.all { it == null || it >= 0 }) { "custom amounts must be >= 0" }
        val tipValue = tipCents(billCents, tip)
        val total = billCents + tipValue
        val custom = customShares.map { it != null }
        val problem = problem(total, customShares)
        val equalCount = custom.count { !it }
        val remainder = total - customShares.sumOf { it ?: 0L }
        val shares = if (problem != null || equalCount == 0) {
            customShares.map { it ?: 0L }
        } else {
            val base = Math.floorDiv(remainder, equalCount.toLong())
            val leftover = remainder - base * equalCount
            val firstEqual = custom.indexOfFirst { !it }
            customShares.mapIndexed { i, amount ->
                when {
                    amount != null -> amount
                    i == firstEqual -> base + leftover
                    else -> base
                }
            }
        }
        return SplitResult(tipValue, total, shares, custom, problem)
    }

    /** Null when [customShares] (null = equal split) can be used with [totalCents]. */
    fun problem(totalCents: Long, customShares: List<Long?>): CustomShareProblem? {
        val sum = customShares.sumOf { it ?: 0L }
        return when {
            sum > totalCents -> CustomShareProblem.OverTotal
            customShares.isNotEmpty() && customShares.all { it != null } && sum != totalCents -> CustomShareProblem.Unassigned
            else -> null
        }
    }

    private const val HALF_CENT_ROUNDING = 50L
    private const val PERCENT = 100L
}
