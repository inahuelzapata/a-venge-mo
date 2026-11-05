package com.splitparty.android.ui.me

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.ui.party.PartyTestTags
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Demo section at the bottom of the Me tab (PoC-12). */
@RunWith(AndroidJUnit4::class)
class DemoResetScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<MeAction>()

    private fun show(reset: DemoResetUi) = compose.setContent {
        SplitPartyTheme {
            MeScreen(
                MeUiState(userId = "annie", displayName = "Annie", transactions = LiveState.Ready(persistentListOf()), reset = reset),
                PaddingValues(),
                onAction = { actions += it }
            )
        }
    }

    private fun scrollToReset() = compose.onNodeWithTag(MeTestTags.LIST).performScrollToNode(hasTestTag(MeTestTags.DEMO_RESET))

    @Test
    fun resetAsksFirstAndCancelChangesNothing() {
        show(DemoResetUi.Idle)
        scrollToReset()
        compose.onNodeWithText("Demo").assertExists()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET).performClick()
        compose.onNodeWithText("Reset the demo?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<MeAction>(), actions)
    }

    @Test
    fun confirmingCallsReset() {
        show(DemoResetUi.Idle)
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET).performClick()
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()
        assertEquals(listOf<MeAction>(MeAction.ResetDemo), actions)
    }

    @Test
    fun runningShowsProgressAndCannotBeTappedAgain() {
        show(DemoResetUi.Running)
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET).assertIsNotEnabled()
    }

    @Test
    fun failureReadsInlineAndTheButtonWorksAgain() {
        show(DemoResetUi.Failed)
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET_ERROR, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Couldn't reset the demo. Check your connection and try again.").assertExists()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET).performClick()
        compose.onNodeWithText("Reset the demo?").assertExists()
    }
}
