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
}
