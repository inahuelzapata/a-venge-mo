package com.splitparty.android.screenshots

import androidx.compose.foundation.layout.PaddingValues
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.ui.gate.LocalNetworkPermissionScreen
import com.splitparty.android.ui.pay.PayRequestScreen
import com.splitparty.android.ui.setup.SetupRequiredScreen
import org.junit.Rule
import org.junit.Test

class OtherScreensScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule()

    private val target = "Emulator 10.0.2.2:8080 (project demo-split-party)"

    @Test
    fun payRequest() = paparazzi.screen { PayRequestScreen(PaddingValues()) }

    @Test
    fun localNetworkPermission() = paparazzi.screen {
        LocalNetworkPermissionScreen(target, denied = false, onAllow = {}, onOpenSettings = {})
    }

    @Test
    fun localNetworkPermissionDenied() = paparazzi.screen {
        LocalNetworkPermissionScreen(target, denied = true, onAllow = {}, onOpenSettings = {})
    }

    @Test
    fun setupRequired() = paparazzi.screen {
        val reason = "FIREBASE_TARGET is not set. Copy android/firestore.properties.example to firestore.properties and rebuild."
        SetupRequiredScreen(FirestoreTarget.NotConfigured(reason), listOf(reason))
    }

    @Test
    fun setupRequiredHostedWithoutConfig() = paparazzi.screen {
        SetupRequiredScreen(
            FirestoreTarget.Hosted("split-party-prod"),
            listOf("google-services.json missing: the hosted target needs it. See android/README.md.")
        )
    }
}
