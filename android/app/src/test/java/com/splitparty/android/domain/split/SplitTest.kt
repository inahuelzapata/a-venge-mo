package com.splitparty.android.domain.split

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as backend/test/split.test.js and the acceptance criteria. */
class SplitTest {
    @Test
    fun `100 plus 18 percent for three gives 3934 3933 3933`() {
        val split = Split.compute(10_000, Tip.Percent(18), 3)
        assertEquals(1_800L, split.tipCents)
        assertEquals(11_800L, split.totalCents)
        assertEquals(listOf(3_934L, 3_933L, 3_933L), split.shares)
    }

    @Test
    fun `custom 7 on 100 for three gives 3568 3566 3566`() {
        val split = Split.compute(10_000, Tip.Custom(700), 3)
        assertEquals(10_700L, split.totalCents)
        assertEquals(listOf(3_568L, 3_566L, 3_566L), split.shares)
    }

    @Test
    fun `percent tip rounds half up`() {
        assertEquals(500L, Split.tipCents(3_333, Tip.Percent(15)))
        assertEquals(3_833L, Split.compute(3_333, Tip.Percent(15), 1).totalCents)
    }

    @Test
    fun `null tip and zero custom are 0 percent`() {
        assertEquals(0L, Split.tipCents(4_850, null))
        assertEquals(listOf(4_850L), Split.compute(4_850, Tip.ZERO, 1).shares)
    }

    @Test
    fun `shares always sum to the total with leftover on the host`() {
        for (bill in listOf(1L, 2L, 99L, 101L, 3_333L, 999_999L)) {
            for (n in 1..8) {
                for (tip in listOf(null, Tip.Percent(15), Tip.Percent(18), Tip.Percent(20), Tip.Custom(7))) {
                    val split = Split.compute(bill, tip, n)
                    assertEquals(split.totalCents, split.shares.sum())
                    split.shares.drop(1).forEach { assertEquals(split.shares[1], it) }
                    assert(split.shares[0] - split.shares.last() in 0 until n)
                }
            }
        }
    }

    // Custom shares: same cases as backend/test/unit/split.test.js ("Custom shares").

    @Test
    fun `no custom amounts is the unchanged equal split`() {
        val split = Split.compute(10_000, Tip.Percent(18), listOf(null, null, null))
        assertEquals(listOf(3_934L, 3_933L, 3_933L), split.shares)
        assertEquals(listOf(false, false, false), split.custom)
        assertEquals(null, split.problem)
    }

    @Test
    fun `60 dollars - Maxi 10, Annie 35, Christophe the rest 15`() {
        val split = Split.compute(6_000, Tip.ZERO, listOf(1_000L, 3_500L, null))
        assertEquals(listOf(1_000L, 3_500L, 1_500L), split.shares)
        assertEquals(listOf(true, true, false), split.custom)
        assertEquals(true, split.fits)
    }

    @Test
    fun `clearing Annie splits the remaining 50 between Annie and Christophe`() {
        assertEquals(listOf(1_000L, 2_500L, 2_500L), Split.compute(6_000, Tip.ZERO, listOf(1_000L, null, null)).shares)
    }

    @Test
    fun `leftover cents go to the host on the equal split, otherwise to the first joinee on it`() {
        val hostEqual = Split.compute(10_000, Tip.Percent(18), listOf(null, 1_001L, null, null))
        assertEquals(listOf(3_601L, 1_001L, 3_599L, 3_599L), hostEqual.shares)
        val hostCustom = Split.compute(10_000, Tip.Percent(18), listOf(1_001L, null, null, null))
        assertEquals(listOf(1_001L, 3_601L, 3_599L, 3_599L), hostCustom.shares)
        assertEquals(11_800L, hostEqual.shares.sum())
        assertEquals(11_800L, hostCustom.shares.sum())
    }

    @Test
    fun `every member custom summing exactly to the total`() {
        val split = Split.compute(6_000, Tip.ZERO, listOf(1_000L, 3_500L, 1_500L))
        assertEquals(listOf(1_000L, 3_500L, 1_500L), split.shares)
        assertEquals(null, split.problem)
    }

    @Test
    fun `a zero custom share is valid`() {
        assertEquals(listOf(3_000L, 3_000L, 0L), Split.compute(6_000, Tip.ZERO, listOf(null, null, 0L)).shares)
    }

    @Test
    fun `over the total and every member custom not adding up are rejected`() {
        val over = Split.compute(6_000, Tip.ZERO, listOf(null, 3_500L, 3_500L))
        assertEquals(CustomShareProblem.OverTotal, over.problem)
        assertEquals(false, over.fits)
        assertEquals(CustomShareProblem.Unassigned, Split.problem(6_000, listOf(1_000L, 1_000L, 1_000L)))
        assertEquals(null, Split.problem(6_000, listOf(null, 6_000L, null)))
    }

    @Test
    fun `with custom amounts the shares always sum to the total`() {
        for (bill in 0L until 3_000L step 13) {
            for (n in 2..8) {
                val split = Split.compute(bill, Tip.Percent(20), List(n) { i -> if (i == 1) bill / 3 else null })
                assertEquals(split.totalCents, split.shares.sum())
            }
        }
    }
}
