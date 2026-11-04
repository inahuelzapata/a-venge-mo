package com.splitparty.android.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.splitparty.android.ui.shell.BottomBar
import com.splitparty.android.ui.shell.Tab
import org.junit.Rule
import org.junit.Test

/**
 * The shell's bar with the raised Pay/Request button. The screen snapshots render
 * stateless screens without the shell, so this is the only golden showing it.
 */
class BottomBarScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule()

    @Test
    fun homeSelected() = paparazzi.screen { Bar(Tab.Home) }

    @Test
    fun paySelected() = paparazzi.screen { Bar(Tab.Pay) }
}

@Composable
private fun Bar(current: Tab) {
    Box(Modifier.fillMaxSize()) {
        BottomBar(current = current, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
