package com.splitparty.android.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<LoginAction>()

    /** Hosts the stateless screen with a tiny state holder standing in for the ViewModel. */
    private fun show(initial: LoginUiState = LoginUiState(targetSummary = "Emulator test")) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            SplitPartyTheme {
                LoginScreen(
                    state = state,
                    onAction = { action ->
                        actions += action
                        if (action is LoginAction.UsernameChanged) state = state.copy(username = action.value)
                    }
                )
            }
        }
    }

    @Test
    fun signInIsDisabledUntilSomethingIsTyped() {
        show()
        compose.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()
        compose.onNodeWithTag(LoginTestTags.USERNAME).performTextInput("maxi")
        compose.onNodeWithTag(LoginTestTags.SUBMIT).assertIsEnabled().performClick()
        assertEquals(listOf(LoginAction.UsernameChanged("maxi"), LoginAction.Submit), actions)
    }

    @Test
    fun showsTheInlineErrorAndTarget() {
        show(LoginUiState(username = "thor", error = LoginViewModel.noAccount("thor"), targetSummary = "Emulator test"))
        compose.onNodeWithText(LoginViewModel.noAccount("thor")).assertIsDisplayed()
        compose.onNodeWithText("Emulator test").assertIsDisplayed()
    }
}
