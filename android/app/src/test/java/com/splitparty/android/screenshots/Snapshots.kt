package com.splitparty.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme

/**
 * One fixed phone so goldens only change when the UI does: the reference
 * iPhone's geometry (402 x 874 dp at 3x, 1206 x 2622 px), so Android renders
 * are pixel-comparable with docs/design/ios-captures/.
 */
val SnapshotDevice = DeviceConfig.PIXEL_5.copy(
    screenWidth = 1206,
    screenHeight = 2622,
    xdpi = 480,
    ydpi = 480,
    density = Density.XXHIGH
)

/**
 * [fullResolution] keeps the device's 1206 x 2622 px instead of Paparazzi's
 * 1000 px thumbnail; the parity renders need it for 1 dp comparisons.
 */
fun snapshotRule(fontScale: Float = 1f, fullResolution: Boolean = false) = Paparazzi(
    deviceConfig = SnapshotDevice.copy(fontScale = fontScale),
    useDeviceResolution = fullResolution,
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
