package com.splitparty.android.ui.join

import app.cash.turbine.test
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.MainDispatcherRule
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.party
import com.splitparty.android.testing.TestData.server
import kotlin.random.Random
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JoinViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val users = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }
    private val parties = FakePartyRepository().apply { partyFlow("p1").value = server(party(host = "annie")) }

    private fun TestScope.viewModel() = JoinViewModel("p1", "maxi", parties, ObserveUsersUseCase(users), {
        testScheduler.currentTime
    }, Random(3))

    private fun JoinUiState.correct() = options.indexOf(TestData.CODE)

    private fun JoinUiState.wrong() = options.indexOfFirst { it != TestData.CODE }

    @Test
    fun `four options, one of them the host's code, for the named host`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(JoinPhase.Choosing, state.phase)
            assertEquals("Annie", state.hostName)
            assertEquals(4, state.options.toSet().size)
            assertEquals(1, state.options.count { it == TestData.CODE })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `correct pick joins without counting an attempt`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.state.test {
                vm.pick(expectMostRecentItem().correct())
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(JoinEvent.Joined("p1"), awaitItem())
        }
        assertEquals(listOf("p1" to "maxi"), parties.joins)
        assertTrue(parties.wrongAttempts.isEmpty())
    }

    @Test
    fun `wrong picks count, reshuffle, and the third shakes and then locks the party`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.state.test {
                var state = expectMostRecentItem()
                repeat(3) { n ->
                    val before = state.options
                    val picked = state.wrong()
                    vm.pick(picked)
                    runCurrent()
                    state = expectMostRecentItem()
                    if (n < 2) {
                        // The picked option shakes in place first...
                        assertEquals(picked, state.wrongPick)
                        assertEquals(before, state.options)
                        assertTrue(state.showWrongCode)
                        advanceTimeBy(JoinViewModel.SHAKE_MS)
                        runCurrent()
                        state = expectMostRecentItem()
                        // ...then the order changes, the error stays.
                        assertEquals(JoinPhase.Choosing, state.phase)
                        assertTrue(state.showWrongCode)
                        assertTrue(state.options.contains(TestData.CODE))
                        assertNotEquals("order changes after a wrong pick", before, state.options)
                    } else {
                        // The third pick still shakes before the locked state shows.
                        assertEquals(JoinPhase.Choosing, state.phase)
                        assertEquals(picked, state.wrongPick)
                        advanceTimeBy(JoinViewModel.SHAKE_MS)
                        runCurrent()
                        state = expectMostRecentItem()
                    }
                }
                assertEquals(JoinPhase.Locked, state.phase)
                cancelAndIgnoreRemainingEvents()
            }
            repeat(3) { assertEquals(JoinEvent.WrongCode, awaitItem()) }
        }
        assertEquals(3, parties.wrongAttempts.size)
        assertTrue(parties.joins.isEmpty())
    }

    @Test
    fun `a party already locked for this user opens locked`() = runTest {
        parties.partyFlow("p1").value = server(party(host = "annie", wrongAttempts = mapOf("maxi" to 3L)))
        val vm = viewModel()
        vm.state.test {
            assertEquals(JoinPhase.Locked, expectMostRecentItem().phase)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
