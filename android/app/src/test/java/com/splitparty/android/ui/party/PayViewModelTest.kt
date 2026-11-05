package com.splitparty.android.ui.party

import app.cash.turbine.test
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.repository.PartyWriteResult
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.party
import com.splitparty.android.testing.TestData.server
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The host's Pay (PoC-11): gating, one settle per tap, failures inline, and the settlement on every phone. */
class PayViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val users = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }
    private val parties = FakePartyRepository()

    private fun TestScope.viewModel(userId: String) = PartyViewModel(
        partyId = "p1",
        userId = userId,
        parties = parties,
        observeUsers = ObserveUsersUseCase(users),
        connectivity = FakeConnectivityObserver(),
        clock = { testScheduler.currentTime }
    )

    private fun push(party: Party) {
        parties.partyFlow("p1").value = LiveEvent.Data(party, fromCache = false)
    }

    private val everyone = listOf("annie", "maxi", "christophe")

    @Test
    fun `pay is disabled with the total reason, then the join reason, and members have no pay at all`() = runTest {
        val host = viewModel("annie")
        val member = viewModel("maxi")
        host.state.test {
            member.state.test {
                push(party(members = listOf("annie", "maxi")))
                assertNull(expectMostRecentItem().pay)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PayState.NeedsTotal, expectMostRecentItem().pay)
            push(party(billCents = 10_000))
            assertEquals(PayState.NeedsMembers, expectMostRecentItem().pay)
            push(party(members = listOf("annie", "maxi"), billCents = 10_000))
            assertEquals(PayState.Ready, expectMostRecentItem().pay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an expired party cannot be paid and nothing is written`() = runTest {
        val host = viewModel("annie")
        push(party(members = everyone, billCents = 10_000))
        host.state.test {
            expectMostRecentItem()
            testScheduler.advanceTimeBy(Party.TTL_MILLIS + 1)
            runCurrent()
            assertEquals(PayState.Expired, expectMostRecentItem().pay)
            host.pay()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(parties.writes.any { it.startsWith("settle") })
    }

    @Test
    fun `a double tap settles once and every phone sees the settlement with its own amount`() = runTest {
        val host = viewModel("annie")
        val member = viewModel("maxi")
        push(party(members = everyone, billCents = 10_000, tip = Tip.Percent(18)))
        val gate = CompletableDeferred<Unit>().also { parties.writeGate = it }
        member.events.test {
            member.state.test {
                host.state.test {
                    expectMostRecentItem()
                    host.pay()
                    host.pay()
                    runCurrent()
                    assertTrue(expectMostRecentItem().paying)
                    gate.complete(Unit)
                    runCurrent()
                    val settled = expectMostRecentItem()
                    assertEquals(PartyPhase.Settled, settled.phase)
                    assertEquals(SettlementUi(isHost = true, amountCents = 7_866, animate = true), settled.settlement)
                    assertTrue(settled.members.all { it.paid })
                    cancelAndIgnoreRemainingEvents()
                }
                assertEquals(SettlementUi(isHost = false, amountCents = 3_933, animate = true), expectMostRecentItem().settlement)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.Settled, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("settle"), parties.writes)
    }

    @Test
    fun `a retry after the party is already paid writes nothing more`() = runTest {
        push(party(members = everyone, billCents = 10_000, tip = Tip.Percent(18)))
        parties.settle("p1", "annie")
        parties.settle("p1", "annie")
        assertEquals(listOf("settle", "settle:noop"), parties.writes)
    }

    @Test
    fun `a rejected pay is an inline error and the party stays open`() = runTest {
        val host = viewModel("annie")
        push(party(members = everyone, billCents = 10_000))
        parties.writeResult = PartyWriteResult.Failed
        host.state.test {
            expectMostRecentItem()
            host.pay()
            runCurrent()
            val after = expectMostRecentItem()
            assertEquals(WriteError.Failed, after.writeError)
            assertEquals(PartyPhase.Open, after.phase)
            assertFalse(after.paying)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening an already paid party shows it settled without replaying the animation`() = runTest {
        val member = viewModel("christophe")
        push(party(members = everyone, billCents = 10_000, tip = Tip.Percent(18), status = PartyStatus.Paid))
        member.events.test {
            member.state.test {
                assertEquals(SettlementUi(isHost = false, amountCents = 3_933, animate = false), expectMostRecentItem().settlement)
                cancelAndIgnoreRemainingEvents()
            }
            expectNoEvents()
        }
    }

    @Test
    fun `a party deleted by a demo reset sends this phone back to Home`() = runTest {
        val member = viewModel("maxi")
        push(party(members = listOf("annie", "maxi")))
        member.events.test {
            member.state.test {
                expectMostRecentItem()
                parties.partyFlow("p1").value = LiveEvent.Data(null, fromCache = true)
                parties.partyFlow("p1").value = LiveEvent.Data(null, fromCache = false)
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.Gone, awaitItem())
            expectNoEvents()
        }
    }
}
