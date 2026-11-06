package com.splitparty.android.ui.home

import app.cash.turbine.test
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.usecase.ObserveFeedUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeDemoSettingsRepository
import com.splitparty.android.testing.FakeLastPartyRepository
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.testing.TestData.tx
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeSplitPartyRepository()
    private val connectivity = FakeConnectivityObserver()
    private val parties = FakePartyRepository()
    private val demoSettings = FakeDemoSettingsRepository()
    private var now = 1_000L

    private val lastParty = FakeLastPartyRepository()

    // Lazy: built inside the test, after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        HomeViewModel(
            userId = "maxi",
            observeFeed = ObserveFeedUseCase(repository, connectivity),
            observeUsers = ObserveUsersUseCase(repository),
            parties = parties,
            demoSettings = demoSettings,
            connectivity = connectivity,
            clock = { now },
            lastParty = lastParty
        )
    }

    private val state get() = viewModel.state.value

    @Test
    fun `shows the fallback name until users load, then the display name`() {
        assertEquals("Maxi", state.currentUserName)
        assertEquals(LiveState.Loading, state.feed)
        repository.users.value = server(mapOf("maxi" to TestData.maxi.copy(displayName = "Maximiliano")))
        assertEquals("Maximiliano", state.currentUserName)
        assertEquals(TestData.maxi.id, state.users.keys.single())
    }

    @Test
    fun `first load highlights nothing`() {
        repository.feed.value = server(listOf(tx("a"), tx("b")))
        assertEquals(LiveState.Ready(listOf(tx("a"), tx("b"))), state.feed)
        assertTrue(state.highlighted.isEmpty())
    }

    @Test
    fun `a new row is highlighted, then released after the hold`() = runTest {
        assertEquals(LiveState.Loading, state.feed) // subscribe before the snapshots arrive
        repository.feed.value = server(listOf(tx("a")))
        repository.feed.value = server(listOf(tx("new"), tx("a")))
        assertEquals(setOf("new"), state.highlighted)

        advanceTimeBy(HomeViewModel.HIGHLIGHT_HOLD_MS - 1)
        assertEquals(setOf("new"), state.highlighted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(state.highlighted.isEmpty())
    }

    @Test
    fun `going offline shows the offline message`() {
        repository.feed.value = server(listOf(tx("a")))
        connectivity.isOnline.value = false
        assertEquals(LiveState.Unreachable(OFFLINE_MESSAGE), state.feed)
    }

    @Test
    fun `a relaunch after the host cancelled the last party opens it, so the cancelled state shows`() = runTest {
        lastParty.partyId = "p1"
        parties.partyFlow("p1").value = server(
            TestData.party(host = "annie", members = listOf("annie", "maxi"), status = PartyStatus.Cancelled)
                .copy(cancelledAtMillis = now - 60_000)
        )
        viewModel.events.test {
            parties.open.value = server(emptyList())
            runCurrent()
            assertEquals(HomeEvent.OpenParty("p1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a relaunch long after a cancel, or for a party that was not cancelled, stays on Home`() = runTest {
        lastParty.partyId = "p1"
        now = HomeViewModel.REOPEN_CANCELLED_WINDOW_MS + 10_000
        parties.partyFlow("p1").value = server(
            TestData.party(host = "annie", members = listOf("annie", "maxi"), status = PartyStatus.Cancelled)
                .copy(cancelledAtMillis = 0L)
        )
        viewModel.events.test {
            parties.open.value = server(emptyList())
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(lastParty.partyId)
    }
}
