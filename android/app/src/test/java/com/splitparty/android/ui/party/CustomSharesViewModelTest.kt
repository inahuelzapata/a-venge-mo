package com.splitparty.android.ui.party

import app.cash.turbine.test
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.split.CustomShareProblem
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.party
import com.splitparty.android.testing.TestData.server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Custom (uneven) shares, behind the Me tab's "Custom shares" switch: the host's
 * edits, the extended split rule on every phone whatever its switch, and Collect.
 */
class CustomSharesViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val users = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }
    private val parties = FakePartyRepository()

    private fun TestScope.viewModel(userId: String, switchOn: Boolean = true) = PartyViewModel(
        partyId = "p1",
        userId = userId,
        parties = parties,
        observeUsers = ObserveUsersUseCase(users),
        connectivity = FakeConnectivityObserver(),
        clock = { testScheduler.currentTime },
        customSharesSwitch = MutableStateFlow(switchOn)
    )

    private val trio = listOf("maxi", "annie", "christophe")

    /** $60.00 total: bill 6000, 0% tip; Maxi hosts. */
    private fun push(party: Party = party(host = "maxi", members = trio, billCents = 6_000, tip = Tip.ZERO)) {
        parties.partyFlow("p1").value = LiveEvent.Data(party, fromCache = false)
    }

    private fun current(): Party = (parties.partyFlow("p1").value as LiveEvent.Data).value!!

    private fun PartyUiState.amounts() = shares.map { it.shareCents }

    private fun PartyUiState.customFlags() = shares.map { it.isCustom }

    @Test
    fun `host sets 10 for themselves and 35 for Annie - every phone shows 10, 35, 15 with Christophe on the rest`() = runTest {
        push()
        val host = viewModel("maxi")
        val annie = viewModel("annie", switchOn = false)
        val christophe = viewModel("christophe")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("maxi", 1_000)
            runCurrent()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(listOf(1_000L, 3_500L, 1_500L), state.amounts())
            assertEquals(listOf(true, true, false), state.customFlags())
            assertEquals(PayState.Ready, state.pay)
            assertEquals(1_000L, state.total?.shareCents)
            cancelAndIgnoreRemainingEvents()
        }
        for (phone in listOf(annie, christophe)) {
            phone.state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(1_000L, 3_500L, 1_500L), state.amounts())
                assertEquals(listOf(true, true, false), state.customFlags())
                cancelAndIgnoreRemainingEvents()
            }
        }
        assertEquals(listOf("custom:maxi:1000", "custom:annie:3500"), parties.writes)
    }

    @Test
    fun `an amount that would exceed the total is refused with a message and the party keeps its last valid state`() = runTest {
        push()
        val host = viewModel("maxi")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            host.setCustomShare("christophe", 3_500)
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(WriteError.SharesOverTotal, state.writeError)
            assertEquals(listOf(1_250L, 3_500L, 1_250L), state.amounts())
            assertNull(state.sharesProblem)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("custom:annie:3500"), parties.writes)
        assertNull(current().customShareOf("christophe"))
    }

    @Test
    fun `everyone on a custom amount must add up to the total`() = runTest {
        push()
        val host = viewModel("maxi")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("maxi", 1_000)
            runCurrent()
            host.setCustomShare("annie", 1_000)
            runCurrent()
            host.setCustomShare("christophe", 1_000)
            runCurrent()
            assertEquals(WriteError.SharesUnassigned, expectMostRecentItem().writeError)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(current().customShareOf("christophe"))
    }

    @Test
    fun `clearing Annie splits the remaining 50 equally between Annie and Christophe`() = runTest {
        push()
        val host = viewModel("maxi")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("maxi", 1_000)
            runCurrent()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            host.setCustomShare("annie", null)
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(listOf(1_000L, 2_500L, 2_500L), state.amounts())
            assertEquals(listOf(true, false, false), state.customFlags())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lowering the bill under the custom amounts keeps the party, tells the host and disables Collect`() = runTest {
        push()
        val host = viewModel("maxi")
        val christophe = viewModel("christophe", switchOn = false)
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("maxi", 1_000)
            runCurrent()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            host.confirmBill(4_000)
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(PartyPhase.Open, state.phase)
            assertEquals(SharesProblemUi(CustomShareProblem.OverTotal, 4_500, 4_000), state.sharesProblem)
            assertEquals(PayState.SharesDontFit, state.pay)
            assertEquals(listOf(1_000L, 3_500L, null), state.amounts())
            host.pay()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        christophe.state.test {
            val state = expectMostRecentItem()
            assertEquals(PartyPhase.Open, state.phase)
            assertTrue(state.total!!.sharePending)
            assertNull(state.total!!.shareCents)
            assertEquals(CustomShareProblem.OverTotal, state.sharesProblem?.problem)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(parties.writes.any { it.startsWith("settle") })
        // Fixed: Collect is back.
        host.state.test {
            host.setCustomShare("annie", null)
            runCurrent()
            assertEquals(PayState.Ready, expectMostRecentItem().pay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the host with the switch on gets controls - a switch never changes the shares shown`() = runTest {
        push()
        val hostOn = viewModel("maxi")
        val hostOff = viewModel("maxi", switchOn = false)
        val joineeOn = viewModel("annie", switchOn = true)
        hostOn.state.test {
            expectMostRecentItem()
            hostOn.setCustomShare("annie", 3_500)
            runCurrent()
            assertTrue(expectMostRecentItem().canEditShares)
            cancelAndIgnoreRemainingEvents()
        }
        for (phone in listOf(hostOff, joineeOn)) {
            phone.state.test {
                val state = expectMostRecentItem()
                assertFalse(state.canEditShares)
                assertEquals(listOf(1_250L, 3_500L, 1_250L), state.amounts())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `custom amounts can be set before the total, and apply once it is set`() = runTest {
        push(party(host = "maxi", members = trio))
        val host = viewModel("maxi")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            val before = expectMostRecentItem()
            assertNull(before.total)
            assertEquals(listOf(null, 3_500L, null), before.shares.map { it.shareCents ?: it.customCents })
            host.confirmBill(6_000)
            runCurrent()
            assertEquals(listOf(1_250L, 3_500L, 1_250L), expectMostRecentItem().amounts())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a member who leaves takes their amount and the rest recomputes to the total`() = runTest {
        push()
        val host = viewModel("maxi")
        val annie = viewModel("annie")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("maxi", 1_000)
            runCurrent()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            annie.leave()
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(listOf("maxi", "christophe"), state.shares.map { it.userId })
            assertEquals(listOf(1_000L, 5_000L), state.amounts())
            assertEquals(6_000L, state.amounts().sumOf { it ?: 0L })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `collect settles with the custom shares - host collects 50, Annie paid 35, a 0 share pays nothing`() = runTest {
        push()
        val host = viewModel("maxi")
        val annie = viewModel("annie")
        val christophe = viewModel("christophe")
        host.state.test {
            expectMostRecentItem()
            host.setCustomShare("maxi", 1_000)
            runCurrent()
            host.setCustomShare("annie", 3_500)
            runCurrent()
            host.pay()
            runCurrent()
            assertEquals(5_000L, expectMostRecentItem().settlement?.amountCents)
            cancelAndIgnoreRemainingEvents()
        }
        annie.state.test {
            assertEquals(3_500L, expectMostRecentItem().settlement?.amountCents)
            cancelAndIgnoreRemainingEvents()
        }
        christophe.state.test {
            assertEquals(1_500L, expectMostRecentItem().settlement?.amountCents)
            cancelAndIgnoreRemainingEvents()
        }
        // A joinee on $0.00: Collect is allowed; the settlement writer skips 0 shares.
        push(
            party(host = "maxi", members = trio, billCents = 6_000, tip = Tip.ZERO).let { p ->
                p.copy(members = p.members.map { if (it.userId == "christophe") it.copy(customShareCents = 0) else it })
            }
        )
        assertNull(current().payBlockerAt(0))
        assertEquals(listOf(3_000L, 3_000L, 0L), current().split?.shares)
        assertEquals(PartyStatus.Open, current().status)
    }
}
