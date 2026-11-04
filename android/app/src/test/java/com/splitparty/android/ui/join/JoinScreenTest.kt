package com.splitparty.android.ui.join

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JoinScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val picks = mutableListOf<Int>()
    private val choosing = JoinUiState(
        phase = JoinPhase.Choosing,
        hostName = "Annie",
        options = persistentListOf(
            persistentListOf("🌮", "🔥", "🐶"),
            persistentListOf("🍕", "🍺", "🎉"),
            persistentListOf("🍣", "🍺", "🚀"),
            persistentListOf("🎸", "🍩", "🌵")
        )
    )

    private fun show(state: JoinUiState) = compose.setContent {
        SplitPartyTheme { JoinScreen(state, PaddingValues(), onPick = { picks += it }, onBack = {}) }
    }

    @Test
    fun statesWhosePartyAndReadsOptionsWithEmojiNames() {
        show(choosing)
        compose.onNodeWithText("Join Annie's party").assertIsDisplayed()
        compose.onNodeWithTag(JoinTestTags.option(1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("pizza, beer, party popper")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(listOf(1), picks)
    }

    @Test
    fun wrongPickShowsTheError() {
        show(choosing.copy(wrongPick = 2, wrongPickCount = 1, showWrongCode = true))
        compose.onNodeWithText("That's not the code. Try again.").assertIsDisplayed()
    }

    @Test
    fun lockedExplainsTheLock() {
        show(choosing.copy(phase = JoinPhase.Locked))
        compose.onNodeWithText("Too many wrong codes").assertIsDisplayed()
        compose.onNodeWithText("Back to Home").assertIsDisplayed()
    }
}
