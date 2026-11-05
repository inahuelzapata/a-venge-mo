package com.splitparty.android.ui.me

import app.cash.turbine.test
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.repository.DemoResetFailure
import com.splitparty.android.domain.repository.DemoResetResult
import com.splitparty.android.domain.usecase.ObserveUserTransactionsUseCase
import com.splitparty.android.domain.usecase.ObserveUserUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.domain.usecase.SignOutUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeDemoResetRepository
import com.splitparty.android.testing.FakeDemoSettingsRepository
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.testing.TestData.tx
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MeViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeSplitPartyRepository()
    private val connectivity = FakeConnectivityObserver()
    private val session = FakeSessionRepository(initial = "maxi")
    private val demoSettings = FakeDemoSettingsRepository(initial = true)
    private val demoReset = FakeDemoResetRepository()

    // Lazy: built inside the test, after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        MeViewModel(
            userId = "maxi",
            observeUser = ObserveUserUseCase(repository, connectivity),
            observeUserTransactions = ObserveUserTransactionsUseCase(repository, connectivity),
            observeUsers = ObserveUsersUseCase(repository),
            signOut = SignOutUseCase(session),
            demoSettings = demoSettings,
            demoReset = demoReset
        )
    }

    @Test
    fun `profile, live balance and own transactions`() = runTest {
        viewModel.state.test {
            assertEquals("Maxi", awaitItem().displayName)

            repository.user.value = server(TestData.maxi)
            repository.userTransactions.value = server(listOf(tx("a")))
            val loaded = expectMostRecentItem()
            assertEquals(LiveState.Ready(TestData.maxi), loaded.profile)
            assertEquals("avatar_maxi", loaded.avatar)
            assertEquals(LiveState.Ready(listOf(tx("a"))), loaded.transactions)

            repository.user.value = server(TestData.maxi.copy(balanceCents = 12_345))
            assertEquals(LiveState.Ready(TestData.maxi.copy(balanceCents = 12_345)), awaitItem().profile)
        }
    }

    @Test
    fun `deleted account is Ready(null)`() = runTest {
        viewModel.state.test {
            repository.user.value = server(null)
            assertEquals(LiveState.Ready(null), expectMostRecentItem().profile)
        }
    }

    @Test
    fun `log out clears the session`() = runTest {
        viewModel.onAction(MeAction.LogOut)
        assertNull(session.signedInUserId.value)
    }

    @Test
    fun `demo switch reads and writes the stored value`() = runTest {
        viewModel.state.test {
            assertEquals(true, expectMostRecentItem().demoAtMerchant)
            viewModel.onAction(MeAction.SetDemoAtMerchant(false))
            assertEquals(false, awaitItem().demoAtMerchant)
            assertEquals(false, demoSettings.atMerchant.value)
        }
    }

    @Test
    fun `demo reset shows progress, then success, and ignores a second tap while running`() = runTest {
        val gate = CompletableDeferred<Unit>().also { demoReset.gate = it }
        viewModel.state.test {
            viewModel.onAction(MeAction.ResetDemo)
            viewModel.onAction(MeAction.ResetDemo)
            runCurrent()
            assertEquals(DemoResetUi.Running, expectMostRecentItem().reset)
            gate.complete(Unit)
            runCurrent()
            assertEquals(DemoResetUi.Done, expectMostRecentItem().reset)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, demoReset.calls)
    }

    @Test
    fun `a partial reset shows the function's message and tapping again completes it`() = runTest {
        val partial = "Demo reset did not complete (boom). The database may be partially reset; tap Reset again."
        demoReset.result = DemoResetResult.Failed(DemoResetFailure.Server, partial)
        viewModel.state.test {
            viewModel.onAction(MeAction.ResetDemo)
            runCurrent()
            assertEquals(DemoResetUi.Failed(DemoResetFailure.Server, partial), expectMostRecentItem().reset)
            demoReset.result = DemoResetResult.Done
            viewModel.onAction(MeAction.ResetDemo)
            runCurrent()
            assertEquals(DemoResetUi.Done, expectMostRecentItem().reset)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, demoReset.calls)
    }

    @Test
    fun `an unreachable server is an error and the button works again once back online`() = runTest {
        demoReset.result = DemoResetResult.Failed(DemoResetFailure.Unreachable)
        viewModel.state.test {
            viewModel.onAction(MeAction.ResetDemo)
            runCurrent()
            assertEquals(DemoResetUi.Failed(DemoResetFailure.Unreachable), expectMostRecentItem().reset)
            demoReset.result = DemoResetResult.Done
            viewModel.onAction(MeAction.ResetDemo)
            runCurrent()
            assertEquals(DemoResetUi.Done, expectMostRecentItem().reset)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, demoReset.calls)
    }
}
