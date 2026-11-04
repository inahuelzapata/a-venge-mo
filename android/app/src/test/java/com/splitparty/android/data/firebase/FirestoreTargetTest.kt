package com.splitparty.android.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreTargetTest {
    @Test
    fun `resolves emulator and hosted targets`() {
        assertEquals(
            FirestoreTarget.Emulator("demo-split-party", "10.0.2.2:8080"),
            FirestoreTarget.resolve("emulator", "demo-split-party", "10.0.2.2:8080")
        )
        assertEquals(FirestoreTarget.Hosted("prod"), FirestoreTarget.resolve("hosted", "prod", ""))
    }

    @Test
    fun `missing or invalid settings are NotConfigured`() {
        assertTrue(FirestoreTarget.resolve("", "p", "") is FirestoreTarget.NotConfigured)
        assertTrue(FirestoreTarget.resolve("emulator", "", "h:1") is FirestoreTarget.NotConfigured)
        assertTrue(FirestoreTarget.resolve("emulator", "p", "") is FirestoreTarget.NotConfigured)
        assertTrue(FirestoreTarget.resolve("cloud", "p", "") is FirestoreTarget.NotConfigured)
    }

    @Test
    fun `emulator host must be host colon port`() {
        assertEquals("192.168.1.20" to 8080, FirebaseSetup.parseHostPort("192.168.1.20:8080"))
        assertNull(FirebaseSetup.parseHostPort("192.168.1.20"))
        assertNull(FirebaseSetup.parseHostPort(":8080"))
        assertNull(FirebaseSetup.parseHostPort("host:0"))
        assertNull(FirebaseSetup.parseHostPort("host:70000"))
    }
}
