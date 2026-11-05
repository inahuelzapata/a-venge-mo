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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PartyViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val users = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }
    private val parties = FakePartyRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun TestScope.viewModel(userId: String) = PartyViewModel(
        partyId = "p1",
        userId = userId,
        parties = parties,
        observeUsers = ObserveUsersUseCase(users),
        connectivity = connectivity,
        clock = { testScheduler.currentTime }
    )

    private fun push(party: Party, fromCache: Boolean = false) {
        parties.partyFlow("p1").value = LiveEvent.Data(party, fromCache)
    }

    @Test
    fun `members in join order with the host first, and a haptic when someone joins`() = runTest {
        val vm = viewModel("annie")
        push(party(host = "annie"))
        vm.events.test {
            vm.state.test {
                val opened = expectMostRecentItem()
                assertEquals(PartyPhase.Open, opened.phase)
                assertEquals(listOf("Annie"), opened.members.map { it.firstName })
                assertTrue(opened.members.single().isHost)
                assertEquals(Party.TTL_MILLIS, opened.millisLeft)

                push(party(host = "annie", members = listOf("annie", "maxi")))
                push(party(host = "annie", members = listOf("annie", "maxi", "christophe")))
                assertEquals(listOf("Annie", "Maxi", "Christophe"), expectMostRecentItem().members.map { it.firstName })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.MemberJoined, awaitItem())
            assertEquals(PartyEvent.MemberJoined, awaitItem())
        }
    }

    @Test
    fun `the joinee's own join is not a haptic for them`() = runTest {
        val vm = viewModel("maxi")
        vm.events.test {
            vm.state.test {
                push(party(host = "annie"))
                push(party(host = "annie", members = listOf("annie", "maxi")))
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            expectNoEvents()
        }
    }

    @Test
    fun `expired by the clock even while stored open, and only the host writes it`() = runTest {
        val host = viewModel("annie")
        val member = viewModel("maxi")
        push(party(host = "annie", members = listOf("annie", "maxi")))
        host.state.test {
            member.state.test {
                advanceTimeBy(Party.TTL_MILLIS - 1_000)
                runCurrent()
                assertEquals(1_000L, expectMostRecentItem().millisLeft)
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(PartyPhase.Expired, expectMostRecentItem().phase)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyPhase.Expired, expectMostRecentItem().phase)
            assertEquals(listOf("p1"), parties.expired)

            // The write landed (the fake stores it): the retry loop stops.
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(listOf("p1"), parties.expired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cached snapshot or no network shows reconnecting, a server one clears it`() = runTest {
        val vm = viewModel("maxi")
        vm.state.test {
            push(party(host = "annie", members = listOf("annie", "maxi")), fromCache = true)
            assertTrue(expectMostRecentItem().reconnecting)
            push(party(host = "annie", members = listOf("annie", "maxi", "christophe")))
            val caughtUp = expectMostRecentItem()
            assertEquals(false, caughtUp.reconnecting)
            assertEquals(3, caughtUp.members.size)
            connectivity.isOnline.value = false
            assertTrue(expectMostRecentItem().reconnecting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a missing party reads as not found`() = runTest {
        val vm = viewModel("maxi")
        vm.state.test {
            parties.partyFlow("p1").value = server(null)
            assertEquals(PartyPhase.NotFound, expectMostRecentItem().phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no bill reads as waiting, host and member alike`() = runTest {
        val host = viewModel("annie")
        val member = viewModel("maxi")
        push(party(host = "annie", members = listOf("annie", "maxi")))
        host.state.test {
            val s = expectMostRecentItem()
            assertTrue(s.isHost)
            assertNull(s.total)
            cancelAndIgnoreRemainingEvents()
        }
        member.state.test {
            val s = expectMostRecentItem()
            assertEquals(false, s.isHost)
            assertEquals("Annie", s.hostName)
            assertNull(s.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `100 plus 18 percent for three gives host 39_34 and others 39_33, and a removal recomputes`() = runTest {
        val three = listOf("maxi", "annie", "christophe")
        val host = viewModel("maxi")
        val member = viewModel("christophe")
        push(party(host = "maxi", members = three, billCents = 10_000, tip = Tip.Percent(18)))
        host.state.test {
            val t = expectMostRecentItem().total!!
            assertEquals(11_800L, t.totalCents)
            assertEquals(3, t.headcount)
            assertEquals(3_934L, t.shareCents)
            assertEquals(TipChoice.Percent(18), t.tip)
            cancelAndIgnoreRemainingEvents()
        }
        member.events.test {
            member.state.test {
                assertEquals(3_933L, expectMostRecentItem().total!!.shareCents)
                push(party(host = "maxi", members = three.take(2), billCents = 10_000, tip = Tip.Percent(18)))
                val after = expectMostRecentItem()
                // Christophe is gone from the list: no share for him any more.
                assertNull(after.total!!.shareCents)
                assertEquals(2, after.total!!.headcount)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.Removed("Maxi"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a share change is a haptic event`() = runTest {
        val vm = viewModel("annie")
        vm.events.test {
            vm.state.test {
                push(party(host = "maxi", members = listOf("maxi", "annie"), billCents = 10_000, tip = Tip.ZERO))
                push(party(host = "maxi", members = listOf("maxi", "annie", "christophe"), billCents = 10_000, tip = Tip.ZERO))
                assertEquals(3_333L, expectMostRecentItem().total!!.shareCents)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.MemberJoined, awaitItem())
            assertEquals(PartyEvent.ShareChanged, awaitItem())
        }
    }

    @Test
    fun `host confirms a bill with the 0 percent tip, then picks custom 7`() = runTest {
        val vm = viewModel("maxi")
        push(party(host = "maxi", members = listOf("maxi", "annie", "christophe")))
        vm.events.test {
            vm.state.test {
                expectMostRecentItem()
                vm.confirmBill(10_000)
                runCurrent()
                assertEquals(TipChoice.Zero, expectMostRecentItem().total!!.tip)
                vm.selectTip(TipChoice.Custom(700))
                runCurrent()
                val t = expectMostRecentItem().total!!
                assertEquals(10_700L, t.totalCents)
                assertEquals(3_568L, t.shareCents)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("bill:10000", "tip:${Tip.Custom(700)}"), parties.writes)
    }

    @Test
    fun `members cannot write the bill or tip, and a rejected write shows inline`() = runTest {
        val member = viewModel("annie")
        push(party(host = "maxi", members = listOf("maxi", "annie")))
        member.state.test {
            expectMostRecentItem()
            member.confirmBill(4_850)
            member.selectTip(TipChoice.Percent(18))
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(parties.writes.isEmpty())

        val host = viewModel("maxi")
        parties.writeResult = PartyWriteResult.Failed
        host.state.test {
            expectMostRecentItem()
            host.confirmBill(4_850)
            runCurrent()
            val s = expectMostRecentItem()
            assertEquals(WriteError.Failed, s.writeError)
            assertNull(s.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaving is not a removal, and the host cannot leave`() = runTest {
        val vm = viewModel("annie")
        push(party(host = "maxi", members = listOf("maxi", "annie")))
        vm.events.test {
            vm.state.test {
                expectMostRecentItem()
                vm.leave()
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(PartyEvent.Left, awaitItem())
            expectNoEvents()
        }
        val host = viewModel("maxi")
        host.state.test {
            expectMostRecentItem()
            host.leave()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("leave:annie"), parties.writes)
    }

    @Test
    fun `after expiry the total stays frozen and nothing is written`() = runTest {
        val vm = viewModel("maxi")
        push(
            party(host = "maxi", members = listOf("maxi", "annie"), billCents = 10_000, tip = Tip.Percent(20), status = PartyStatus.Expired)
        )
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(PartyPhase.Expired, s.phase)
            assertEquals(12_000L, s.total!!.totalCents)
            vm.confirmBill(20_000)
            vm.selectTip(TipChoice.Zero)
            vm.removeMember("annie")
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(parties.writes.isEmpty())
    }

    @Test
    fun `countdown formats minutes and seconds`() {
        assertEquals("5:00", formatCountdown(Party.TTL_MILLIS))
        assertEquals("4:59", formatCountdown(299_000))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(-5))
    }
}
