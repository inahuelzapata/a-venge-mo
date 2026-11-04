package com.splitparty.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameTest {
    @Test
    fun `normalize trims and lower-cases`() {
        assertEquals("maxi", Username.normalize("  MaXi "))
        assertEquals("christophe", Username.normalize("Christophe"))
    }

    @Test
    fun `isValid accepts schema usernames and rejects path characters`() {
        assertTrue(Username.isValid("annie"))
        assertTrue(Username.isValid("user_01"))
        assertFalse(Username.isValid(""))
        assertFalse(Username.isValid("ab"))
        assertFalse(Username.isValid("a/b"))
        assertFalse(Username.isValid("a".repeat(21)))
    }

    @Test
    fun `fallbackName capitalises the id`() {
        assertEquals("Maxi", Username.fallbackName("maxi"))
    }
}
