package com.splitparty.android.domain.live

import app.cash.turbine.test
import com.splitparty.android.domain.model.LiveEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStateTest {
    private val events = MutableStateFlow<LiveEvent<Int>>(LiveEvent.Pending)
    private val online = MutableStateFlow(true)

    @Test
    fun `server data is Ready`() = runTest {
        events.value = LiveEvent.Data(1, fromCache = false)
        events.toLiveState(online).test {
            assertEquals(LiveState.Loading, awaitItem())
            assertEquals(LiveState.Ready(1), awaitItem())
        }
    }

    @Test
    fun `offline is Unreachable at once, and Ready again when back online`() = runTest {
        events.value = LiveEvent.Data(1, fromCache = false)
        online.value = false
        events.toLiveState(online).test {
            assertEquals(LiveState.Loading, awaitItem())
            assertEquals(LiveState.Unreachable(OFFLINE_MESSAGE), awaitItem())
            assertEquals(0L, currentTime)
            online.value = true
            assertEquals(LiveState.Ready(1), awaitItem())
        }
    }

    @Test
    fun `no server snapshot within the timeout is Unreachable`() = runTest {
        events.toLiveState(online, serverTimeoutMs = 5_000).test {
            assertEquals(LiveState.Loading, awaitItem())
            assertEquals(LiveState.Unreachable(UNREACHABLE_MESSAGE), awaitItem())
            assertEquals(5_000L, currentTime)
        }
    }

    @Test
    fun `cache-only data keeps the screen until the timeout`() = runTest {
        events.value = LiveEvent.Data(1, fromCache = false)
        events.toLiveState(online, serverTimeoutMs = 5_000).test {
            skipItems(2) // Loading, Ready(1)
            events.value = LiveEvent.Data(1, fromCache = true)
            assertEquals(LiveState.Unreachable(UNREACHABLE_MESSAGE), awaitItem())
            assertEquals(5_000L, currentTime)
            events.value = LiveEvent.Data(2, fromCache = false)
            assertEquals(LiveState.Ready(2), awaitItem())
        }
    }

    @Test
    fun `a failed listener is Unreachable at once`() = runTest {
        events.value = LiveEvent.Failed(IllegalStateException("missing index"))
        events.toLiveState(online).test {
            assertEquals(LiveState.Loading, awaitItem())
            assertEquals(LiveState.Unreachable(UNREACHABLE_MESSAGE), awaitItem())
        }
    }

    @Test
    fun `map transforms only Ready`() {
        assertEquals(LiveState.Ready(2), LiveState.Ready(1).map { it * 2 })
        assertEquals(LiveState.Loading, (LiveState.Loading as LiveState<Int>).map { it * 2 })
    }
}
