package com.splitparty.android.ui.me

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.repository.DemoResetFailure
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
        compose.onNodeWithTag(MeTestTags.DEMO_RESET)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Resetting the demo"))
    }

    @Test
    fun offlineFailureReadsInlineAndTheButtonWorksAgain() {
        show(DemoResetUi.Failed(DemoResetFailure.Unreachable))
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET_ERROR, useUnmergedTree = true)
            .assertTextEquals("Couldn't reach the server, so nothing was reset. Check your connection and try again.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        compose.onNodeWithTag(MeTestTags.DEMO_RESET).performClick()
        compose.onNodeWithText("Reset the demo?").assertExists()
    }

    @Test
    fun missingFunctionIsNamed() {
        show(DemoResetUi.Failed(DemoResetFailure.NotDeployed))
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET_ERROR, useUnmergedTree = true)
            .assertTextEquals("Demo reset isn't set up on the server (the resetDemo function is not deployed). Nothing was reset.")
    }

    @Test
    fun theFunctionsOwnMessageIsShown() {
        val partial = "Demo reset did not complete (boom). The database may be partially reset; tap Reset again."
        show(DemoResetUi.Failed(DemoResetFailure.Server, partial))
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET_ERROR, useUnmergedTree = true).assertTextEquals(partial)
    }

    @Test
    fun successIsAnnounced() {
        show(DemoResetUi.Done)
        scrollToReset()
        compose.onNodeWithTag(MeTestTags.DEMO_RESET_DONE, useUnmergedTree = true)
            .assertTextEquals("Demo reset. Every phone is back to the seeded state.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
