package com.splitparty.android.ui.home

import com.splitparty.android.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

/** After a Pay, the payments drop onto the feed one at a time, oldest first, not all at once. */
class FeedStaggerTest {
    private fun tx(id: String) = Transaction(id, "a", "b", 100, "memo", null, null, 0, 0, 0)

    private val seeded = listOf(tx("s2"), tx("s1"))

    @Test
    fun theFirstDeliveryIsNotNews() {
        assertEquals(emptyList<String>(), FeedStagger().arrivals(seeded, animate = true))
    }

    @Test
    fun severalNewRowsDropInOldestFirst() {
        val stagger = FeedStagger()
        stagger.arrivals(seeded, animate = true)
        // Newest first, as the feed is ordered: p2 was written after p1.
        assertEquals(listOf("p1", "p2"), stagger.arrivals(listOf(tx("p2"), tx("p1")) + seeded, animate = true))
    }

    @Test
    fun aSingleNewRowOrMotionOffIsNotStaggered() {
        val stagger = FeedStagger()
        stagger.arrivals(seeded, animate = true)
        assertEquals(emptyList<String>(), stagger.arrivals(listOf(tx("p1")) + seeded, animate = true))
        assertEquals(emptyList<String>(), stagger.arrivals(listOf(tx("p3"), tx("p2"), tx("p1")) + seeded, animate = false))
    }
}
