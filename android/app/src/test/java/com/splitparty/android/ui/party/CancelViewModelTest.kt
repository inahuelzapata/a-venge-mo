package com.splitparty.android.ui.party

import app.cash.turbine.test
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.model.PayBlocker
import com.splitparty.android.domain.repository.CancelPartyResult
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakeAnalytics
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeLastPartyRepository
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.party
import com.splitparty.android.testing.TestData.server
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Host cancel (PRD US-12/13, FR-2.8–2.10): host only, its own phase on every phone, no charge, one analytics event. */
class CancelViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val users = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }
    private val parties = FakePartyRepository()
    private val analytics = FakeAnalytics()
    private val lastParty = FakeLastPartyRepository()

    private fun TestScope.viewModel(userId: String, last: FakeLastPartyRepository = lastParty) = PartyViewModel(
        partyId = "p1",
        userId = userId,
        parties = parties,
        observeUsers = ObserveUsersUseCase(users),
        connectivity = FakeConnectivityObserver(),
        clock = { testScheduler.currentTime },
        analytics = analytics,
        lastParty = last
    )

    private fun push(party: Party) {
        parties.partyFlow("p1").value = LiveEvent.Data(party, fromCache = false)
    }

    private val everyone = listOf("annie", "maxi", "christophe")

    @Test
    fun `cancelled is parsed as its own status and blocks pay, never read as expired`() {
        assertEquals(PartyStatus.Cancelled, PartyStatus.fromWire("cancelled"))
        val cancelled = party(status = PartyStatus.Cancelled, members = everyone, billCents = 10_000)
        assertTrue(cancelled.isCancelled)
        assertFalse(cancelled.isActiveAt(0L))
        assertEquals(PayBlocker.Cancelled, cancelled.payBlockerAt(0L))
        assertFalse(cancelled.canCancel("annie", 0L))
        assertTrue(party().canCancel("annie", Party.TTL_MILLIS - 1))
        assertFalse("expired by the clock", party().canCancel("annie", Party.TTL_MILLIS))
        assertFalse("only the host", party(members = everyone).canCancel("maxi", 0L))
    }

    @Test
    fun `only the host gets the cancel control, and only while the party is open`() = runTest {
        val host = viewModel("annie")
        val member = viewModel("maxi")
        push(party(members = everyone))
        member.state.test {
            assertFalse(expectMostRecentItem().canCancel)
            cancelAndIgnoreRemainingEvents()
        }
        host.state.test {
            assertTrue(expectMostRecentItem().canCancel)
            push(party(members = everyone, status = PartyStatus.Paid))
            assertFalse(expectMostRecentItem().canCancel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a member cannot cancel and nothing is written`() = runTest {
        val member = viewModel("maxi")
        push(party(members = everyone))
        member.state.test {
            expectMostRecentItem()
            member.cancel()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(parties.writes.none { it.startsWith("cancel") })
    }

    @Test
    fun `the host cancels with no total and nobody in, goes Home, and party_cancelled is recorded once`() = runTest {
        val host = viewModel("annie")
        push(party())
        host.events.test {
            host.state.test {
                expectMostRecentItem()
                testScheduler.advanceTimeBy(42_000)
                runCurrent()
                host.cancel()
                host.cancel()
                runCurrent()
                assertEquals(PartyPhase.Cancelled, expectMostRecentItem().phase)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.CancelledByMe, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("cancel:annie"), parties.writes.filter { it.startsWith("cancel") })
        assertEquals(1, analytics.events.size)
        val event = analytics.events.single()
        assertEquals("party_cancelled", event.name)
        assertEquals("p1", event.parameters["party_id"])
        assertEquals("1", event.parameters["member_count"])
        assertNull("a relaunch does not reopen it for the host", lastParty.partyId)
    }

    @Test
    fun `every joinee's live split turns into the cancelled phase, and pay can no longer run`() = runTest {
        val host = viewModel("annie")
        val memberLastParty = FakeLastPartyRepository()
        val member = viewModel("christophe", memberLastParty)
        push(party(members = everyone, billCents = 10_000, tip = Tip.Percent(18)))
        member.state.test {
            assertEquals(PartyPhase.Open, expectMostRecentItem().phase)
            host.state.test {
                expectMostRecentItem()
                host.cancel()
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }
            val cancelled = expectMostRecentItem()
            assertEquals(PartyPhase.Cancelled, cancelled.phase)
            assertFalse(cancelled.isHost)
            cancelAndIgnoreRemainingEvents()
        }
        host.pay()
        runCurrent()
        assertTrue("nothing is charged", parties.writes.none { it.startsWith("settle") })
        assertEquals("the joinee's relaunch can still show the cancelled state", "p1", memberLastParty.partyId)
    }

    @Test
    fun `a refused cancel leaves the party open, shows the error and records nothing`() = runTest {
        val host = viewModel("annie")
        push(party(members = everyone))
        parties.cancelResult = CancelPartyResult.Failed
        host.state.test {
            expectMostRecentItem()
            host.cancel()
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(PartyPhase.Open, state.phase)
            assertEquals(WriteError.Failed, state.writeError)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(analytics.events.isEmpty())
    }
}
