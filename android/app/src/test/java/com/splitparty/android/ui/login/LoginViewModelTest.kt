package com.splitparty.android.ui.login

import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.domain.model.UserLookup
import com.splitparty.android.domain.usecase.SignInUseCase
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeSplitPartyRepository()
    private val session = FakeSessionRepository()

    // Lazy: built inside the test, after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        LoginViewModel(
            SignInUseCase(repository, session),
            FirestoreTarget.Emulator("demo-split-party", "10.0.2.2:8080")
        )
    }

    private fun type(text: String) = viewModel.onAction(LoginAction.UsernameChanged(text))

    @Test
    fun `starts empty with the target summary and submit disabled`() {
        val state = viewModel.state.value
        assertEquals("", state.username)
        assertFalse(state.canSubmit)
        assertEquals("Emulator 10.0.2.2:8080 (project demo-split-party)", state.targetSummary)
    }

    @Test
    fun `blank input keeps submit disabled and does nothing`() = runTest {
        type("   ")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onAction(LoginAction.Submit)
        assertTrue(repository.lookups.isEmpty())
    }

    @Test
    fun `unknown username shows the inline error, and editing clears it`() = runTest {
        type("thor")
        viewModel.onAction(LoginAction.Submit)
        assertEquals(LoginViewModel.noAccount("thor"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.busy)

        type("tho")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `known username signs in`() = runTest {
        type(" MaXi ")
        viewModel.onAction(LoginAction.Submit)
        assertEquals("maxi", session.signedInUserId.value)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `unreachable server shows the connection error`() = runTest {
        repository.lookupOverride = UserLookup.Unreachable
        type("maxi")
        viewModel.onAction(LoginAction.Submit)
        assertEquals(LoginViewModel.UNREACHABLE, viewModel.state.value.error)
    }

    @Test
    fun `busy while the lookup runs, and a second submit is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.lookupGate = gate
        type("maxi")
        viewModel.onAction(LoginAction.Submit)
        assertTrue(viewModel.state.value.busy)
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onAction(LoginAction.Submit)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.busy)
        assertEquals(listOf("maxi"), repository.lookups)
    }
}
