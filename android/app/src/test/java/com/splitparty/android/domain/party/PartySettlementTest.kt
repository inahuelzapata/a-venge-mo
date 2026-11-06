package com.splitparty.android.domain.party

import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.model.PayBlocker
import com.splitparty.android.domain.split.CustomShareProblem
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartySettlementTest {
    @Test
    fun `memo is the merchant name followed by Split Party`() {
        assertEquals("Tony's Split Party", PartySettlement.memo("Tony's"))
    }

    @Test
    fun `a long merchant name is cut so the memo stays within the rules' 80 characters`() {
        val memo = PartySettlement.memo("x".repeat(200))
        assertEquals(PartySettlement.MAX_MEMO_LENGTH, memo.length)
        assertTrue(memo.endsWith(" Split Party"))
    }

    @Test
    fun `transaction id is deterministic per party and payer`() {
        assertEquals("p1_maxi", PartySettlement.transactionId("p1", "maxi"))
    }

    @Test
    fun `pay is blocked until a bill and a joinee exist`() {
        assertEquals(PayBlocker.NoTotal, TestData.party(members = listOf("annie", "maxi")).payBlockerAt(0))
        assertEquals(PayBlocker.NoMembers, TestData.party(billCents = 10_000).payBlockerAt(0))
        assertNull(TestData.party(members = listOf("annie", "maxi"), billCents = 10_000).payBlockerAt(0))
    }

    @Test
    fun `an expired or paid party cannot be paid`() {
        val ready = TestData.party(members = listOf("annie", "maxi"), billCents = 10_000)
        assertEquals(PayBlocker.Expired, ready.payBlockerAt(ready.expiresAtMillis))
        assertEquals(PayBlocker.AlreadyPaid, ready.copy(status = PartyStatus.Paid).payBlockerAt(0))
    }

    @Test
    fun `no tip settles with the 100 dollar bill split 3334, 3333, 3333 and only joinees transacted`() {
        val party = TestData.party(members = listOf("annie", "maxi", "christophe"), billCents = 10_000)
        assertEquals(listOf(3_334L, 3_333L, 3_333L), party.split?.shares)
        assertEquals(listOf("maxi", "christophe"), party.joinees.map { it.userId })
    }

    @Test
    fun `custom amounts that no longer fit the total block Collect until fixed or cleared`() {
        val base = TestData.party(members = listOf("maxi", "annie", "christophe"), billCents = 6_000, tip = Tip.ZERO)
        fun withCustom(vararg amounts: Long?) =
            base.copy(members = base.members.mapIndexed { i, m -> m.copy(customShareCents = amounts[i]) })
        val fits = withCustom(1_000L, 3_500L, null)
        assertNull(fits.payBlockerAt(0))
        assertEquals(listOf(1_000L, 3_500L, 1_500L), fits.split?.shares)
        // The bill drops to $40: 10 + 35 > 40.
        assertEquals(PayBlocker.CustomSharesDontFit, fits.copy(billCents = 4_000).payBlockerAt(0))
        assertEquals(CustomShareProblem.OverTotal, fits.customShareProblemWith("christophe", 3_500L))
        assertNull(fits.customShareProblemWith("annie", null))
        // No total yet: any amount can be set.
        assertNull(base.copy(billCents = null).customShareProblemWith("annie", 999_999L))
    }
}
