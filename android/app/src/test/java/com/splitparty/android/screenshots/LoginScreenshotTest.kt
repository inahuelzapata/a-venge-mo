package com.splitparty.android.screenshots

import com.splitparty.android.ui.login.LoginScreen
import com.splitparty.android.ui.login.LoginUiState
import com.splitparty.android.ui.login.LoginViewModel
import org.junit.Rule
import org.junit.Test

class LoginScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule()

    private val target = "Emulator 10.0.2.2:8080 (project demo-split-party)"

    @Test
    fun empty() = paparazzi.screen { LoginScreen(LoginUiState(targetSummary = target), onAction = {}) }

    @Test
    fun typed() = paparazzi.screen { LoginScreen(LoginUiState(username = "maxi", targetSummary = target), onAction = {}) }

    @Test
    fun busy() = paparazzi.screen {
        LoginScreen(LoginUiState(username = "maxi", busy = true, targetSummary = target), onAction = {})
    }

    @Test
    fun unknownUser() = paparazzi.screen {
        LoginScreen(LoginUiState(username = "thor", error = LoginViewModel.noAccount("thor"), targetSummary = target), onAction = {})
    }

    @Test
    fun unreachable() = paparazzi.screen {
        LoginScreen(LoginUiState(username = "maxi", error = LoginViewModel.UNREACHABLE, targetSummary = target), onAction = {})
    }
}
