package com.splitparty.android.screenshots

import androidx.compose.foundation.layout.PaddingValues
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.home.HomeScreen
import com.splitparty.android.ui.home.HomeUiState
import com.splitparty.android.ui.login.LoginScreen
import com.splitparty.android.ui.login.LoginUiState
import com.splitparty.android.ui.login.LoginViewModel
import org.junit.Rule
import org.junit.Test

/** Largest common system font size: text must wrap, not clip or overlap. */
class LargeFontScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule(fontScale = 1.5f)

    @Test
    fun loginWithError() = paparazzi.screen {
        LoginScreen(
            LoginUiState(username = "thor", error = LoginViewModel.noAccount("thor"), targetSummary = "Emulator 10.0.2.2:8080"),
            onAction = {}
        )
    }

    @Test
    fun homeLoaded() = paparazzi.screen {
        HomeScreen(
            HomeUiState("Maxi", "avatar_maxi", feed = LiveState.Ready(PreviewData.transactions), users = PreviewData.users),
            PaddingValues()
        )
    }
}
