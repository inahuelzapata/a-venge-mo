package com.splitparty.android.ui.home

import app.cash.turbine.test
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.party.BannerSlot
import com.splitparty.android.domain.repository.CreatePartyResult
import com.splitparty.android.domain.usecase.ObserveFeedUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeDemoSettingsRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home's banner slot and "Start a party" (PoC-7/8). Time is the test scheduler's virtual clock. */
class HomeBannerSlotTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }
    private val connectivity = FakeConnectivityObserver()
    private val parties = FakePartyRepository()
    private val demoSettings = FakeDemoSettingsRepository()

    private fun TestScope.viewModel(userId: String = "maxi") = HomeViewModel(
        userId = userId,
        observeFeed = ObserveFeedUseCase(repository, connectivity),
        observeUsers = ObserveUsersUseCase(repository),
        parties = parties,
        demoSettings = demoSettings,
        connectivity = connectivity,
        clock = { testScheduler.currentTime }
    )

    @Test
    fun `demo switch shows or hides the merchant card`() = runTest {
        val vm = viewModel()
        runCurrent()
        assertEquals(BannerSlot.Merchant, vm.state.value.bannerSlot)
        demoSettings.atMerchant.value = false
        runCurrent()
        assertEquals(BannerSlot.None, vm.state.value.bannerSlot)
    }

    @Test
    fun `a friend's open party replaces the merchant card, and it returns when the party expires by the clock`() = runTest {
        val vm = viewModel()
        val annies = party(host = "annie", createdAt = 0L)
        parties.open.value = server(listOf(annies))
        runCurrent()
        assertEquals(BannerSlot.Join(annies), vm.state.value.bannerSlot)

        // Still stored "open", but past expiresAt: gone the moment it passes.
        advanceTimeBy(Party.TTL_MILLIS - 1)
        runCurrent()
        assertEquals(BannerSlot.Join(annies), vm.state.value.bannerSlot)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(BannerSlot.Merchant, vm.state.value.bannerSlot)
    }

    @Test
    fun `no join banner for a locked-out party or a stranger's party`() = runTest {
        val vm = viewModel()
        parties.open.value =
            server(listOf(party(id = "locked", host = "annie", wrongAttempts = mapOf("maxi" to 3L)), party(id = "x", host = "stranger")))
        runCurrent()
        assertEquals(BannerSlot.Merchant, vm.state.value.bannerSlot)
    }

    @Test
    fun `a member sees Back to your party instead of the banner`() = runTest {
        val vm = viewModel()
        val joined = party(host = "annie", members = listOf("annie", "maxi"))
        parties.open.value = server(listOf(joined))
        runCurrent()
        assertEquals(BannerSlot.BackToParty(joined), vm.state.value.bannerSlot)
    }

    @Test
    fun `start creates a party and opens it`() = runTest {
        val vm = viewModel(userId = "annie")
        vm.events.test {
            runCurrent()
            vm.startParty()
            runCurrent()
            assertEquals(HomeEvent.OpenParty("new-party"), awaitItem())
        }
        assertEquals(listOf("annie"), parties.created)
    }

    @Test
    fun `start while in an open party returns to it without creating another`() = runTest {
        val vm = viewModel(userId = "annie")
        val mine = party(id = "mine", host = "annie")
        parties.open.value = server(listOf(mine))
        vm.events.test {
            runCurrent()
            // Relaunch resume fires first for the server snapshot.
            assertEquals(HomeEvent.OpenParty("mine"), awaitItem())
            vm.startParty()
            assertEquals(HomeEvent.OpenParty("mine"), awaitItem())
        }
        assertTrue(parties.created.isEmpty())
    }

    @Test
    fun `failed or offline start shows the inline error and navigates nowhere`() = runTest {
        val vm = viewModel(userId = "annie")
        parties.createResult = CreatePartyResult.Failed
        vm.events.test {
            runCurrent()
            vm.startParty()
            runCurrent()
            assertTrue(vm.state.value.startPartyFailed)
            assertFalse(vm.state.value.startingParty)

            connectivity.isOnline.value = false
            parties.created.clear()
            vm.startParty()
            runCurrent()
            assertTrue(vm.state.value.startPartyFailed)
            assertTrue("offline never attempts a write", parties.created.isEmpty())
            expectNoEvents()
        }
    }

    @Test
    fun `stale hosted parties are expired on launch`() = runTest {
        viewModel(userId = "annie")
        runCurrent()
        assertEquals(listOf("annie"), parties.staleCleanups)
    }
}
