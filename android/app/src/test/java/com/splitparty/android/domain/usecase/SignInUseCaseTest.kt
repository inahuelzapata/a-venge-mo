package com.splitparty.android.domain.usecase

import com.splitparty.android.domain.model.UserLookup
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {
    private val repository = FakeSplitPartyRepository()
    private val session = FakeSessionRepository()
    private val signIn = SignInUseCase(repository, session)

    @Test
    fun `ignores case and surrounding spaces and saves the session`() = runTest {
        assertEquals(SignInResult.Success("maxi"), signIn("  MaXi "))
        assertEquals(listOf("maxi"), repository.lookups)
        assertEquals("maxi", session.signedInUserId.value)
    }

    @Test
    fun `unknown username is UnknownUser with the trimmed input`() = runTest {
        assertEquals(SignInResult.UnknownUser("thor"), signIn(" thor "))
        assertNull(session.signedInUserId.value)
    }

    @Test
    fun `input that can't be a username never reaches the database`() = runTest {
        assertEquals(SignInResult.UnknownUser("a/b"), signIn("a/b"))
        assertTrue(repository.lookups.isEmpty())
    }

    @Test
    fun `no answer from the server is Unreachable`() = runTest {
        repository.lookupOverride = UserLookup.Unreachable
        assertEquals(SignInResult.Unreachable, signIn("maxi"))
        assertNull(session.signedInUserId.value)
    }
}
