package com.splitparty.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme

/** One fixed phone so goldens only change when the UI does. */
val SnapshotDevice = DeviceConfig.PIXEL_5

fun snapshotRule(fontScale: Float = 1f) = Paparazzi(
    deviceConfig = SnapshotDevice.copy(fontScale = fontScale),
    // Compose-only screens: skip the default theme's action bar.
    theme = "android:Theme.Material.Light.NoActionBar",
    maxPercentDifference = 0.1
)

/** Renders [content] the way the app root does: brand theme on the page background. */
fun Paparazzi.screen(name: String? = null, content: @Composable () -> Unit) = snapshot(name) {
    SplitPartyTheme {
        Box(Modifier.fillMaxSize().background(Brand.Page)) { content() }
    }
}
