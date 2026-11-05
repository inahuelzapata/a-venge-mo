package com.splitparty.android.domain.party

import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyMember
import com.splitparty.android.domain.model.PartyStatus
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinCodesTest {
    @Test
    fun paletteMatchesTheSchema() {
        assertEquals(listOf("🍕", "🍔", "🌮", "🍣", "🍩", "🍺", "🎉", "🔥", "🚀", "🎸", "🐶", "🌵"), JoinCodes.PALETTE)
    }

    @Test
    fun randomCodesAreTwoPaletteEmojisWithRepeatsAllowed() {
        val random = Random(1)
        val seen = mutableSetOf<List<String>>()
        repeat(5_000) {
            val code = JoinCodes.random(random)
            assertEquals(2, code.size)
            assertTrue(JoinCodes.isValid(code))
            seen += code
        }
        // Repeats are allowed, as on iOS: all 12 x 12 = 144 codes come up, 🍕🍕 among them.
        assertEquals(144, seen.size)
        assertTrue(listOf("🍕", "🍕") in seen)
    }

    @Test
    fun onlyTwoPaletteEmojisAreValid() {
        assertTrue(JoinCodes.isValid(listOf("🍕", "🍺")))
        assertTrue(JoinCodes.isValid(listOf("🍕", "🍕")))
        assertFalse(JoinCodes.isValid(listOf("🍕")))
        assertFalse(JoinCodes.isValid(listOf("🍕", "🍺", "🎉")))
        assertFalse(JoinCodes.isValid(emptyList()))
        assertFalse(JoinCodes.isValid(listOf("🍕", "😀")))
    }

    @Test
    fun optionsHoldTheRealCodeOnceAndThreeDistinctDecoys() {
        val real = listOf("🍕", "🍺")
        val random = Random(7)
        val orders = mutableSetOf<Int>()
        repeat(300) {
            val options = JoinCodes.options(real, random)
            assertEquals(4, options.size)
            assertEquals(4, options.toSet().size)
            assertEquals(1, options.count { it == real })
            assertTrue(options.all(JoinCodes::isValid))
            assertTrue(options.all { it.size == 2 })
            orders += options.indexOf(real)
        }
        assertEquals(setOf(0, 1, 2, 3), orders)
    }

    @Test
    fun lockIdAndSpokenName() {
        assertEquals("tonys-greenwich_🍕🍺", JoinCodes.lockId("tonys-greenwich", listOf("🍕", "🍺")))
        assertEquals("pizza, beer", JoinCodes.spokenName(listOf("🍕", "🍺")))
    }

    @Test
    fun partyIsExpiredByTheClockWhateverTheStoredStatus() {
        val party = Party(
            "p", "annie", "tonys-greenwich", listOf("🍕", "🍺"), PartyStatus.Open, 0L, Party.TTL_MILLIS,
            listOf(PartyMember("annie", 0L, false)), mapOf("maxi" to 3L)
        )
        assertTrue(party.isActiveAt(Party.TTL_MILLIS - 1))
        assertFalse(party.isActiveAt(Party.TTL_MILLIS))
        assertFalse(party.copy(status = PartyStatus.Paid).isActiveAt(0L))
        assertTrue(party.isLockedOut("maxi"))
        assertFalse(party.isLockedOut("christophe"))
    }
}
