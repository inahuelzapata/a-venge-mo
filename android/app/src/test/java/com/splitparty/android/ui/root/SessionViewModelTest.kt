package com.splitparty.android.ui.root

import com.splitparty.android.domain.usecase.ObserveSessionUseCase
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `follows the stored session`() = runTest {
        val session = FakeSessionRepository()
        val viewModel = SessionViewModel(ObserveSessionUseCase(session))
        assertEquals(SessionState.SignedOut, viewModel.state.value)

        session.signIn("annie")
        assertEquals(SessionState.SignedIn("annie"), viewModel.state.value)

        session.signOut()
        assertEquals(SessionState.SignedOut, viewModel.state.value)
    }
}
