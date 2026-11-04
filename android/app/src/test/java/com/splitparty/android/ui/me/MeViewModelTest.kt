package com.splitparty.android.ui.me

import app.cash.turbine.test
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.usecase.ObserveUserTransactionsUseCase
import com.splitparty.android.domain.usecase.ObserveUserUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.domain.usecase.SignOutUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.testing.TestData.tx
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

    // Lazy: built inside the test, after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        MeViewModel(
            userId = "maxi",
            observeUser = ObserveUserUseCase(repository, connectivity),
            observeUserTransactions = ObserveUserTransactionsUseCase(repository, connectivity),
            observeUsers = ObserveUsersUseCase(repository),
            signOut = SignOutUseCase(session)
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
}
