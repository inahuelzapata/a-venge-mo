package com.splitparty.android.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var partyStarts = 0

    private fun show(state: HomeUiState) = compose.setContent {
        SplitPartyTheme { HomeScreen(state, contentPadding = PaddingValues(), onStartParty = { partyStarts++ }) }
    }

    private fun assertCardShown() {
        compose.onNodeWithContentDescription(BANNER_COPY).assertIsDisplayed()
        compose.onNodeWithText("Start a party").assertIsDisplayed()
    }

    @Test
    fun rowsShowDisplayNamesMemoAndAmount() {
        show(HomeUiState("Maxi", feed = LiveState.Ready(PreviewData.transactions), users = PreviewData.users))
        compose.onNodeWithText("Annie paid", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tony's Split Party", substring = true).assertIsDisplayed()
        compose.onNodeWithText("$31.15", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyFeedShowsTheEmptyState() {
        show(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf())))
        compose.onNodeWithText("No payments yet").assertIsDisplayed()
        assertCardShown()
    }

    @Test
    fun unreachableShowsTheErrorCard() {
        show(HomeUiState("Maxi", feed = LiveState.Unreachable(OFFLINE_MESSAGE)))
        assertCardShown()
        // The card above it pushes the error card below the small test screen's fold.
        compose.onNodeWithTag(HomeTestTags.FEED).performScrollToNode(hasText(OFFLINE_MESSAGE))
        compose.onNodeWithText("Can't load payments").assertIsDisplayed()
        compose.onNodeWithText(OFFLINE_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun cardIsShownWhileTheFeedLoads() {
        show(HomeUiState("Maxi", feed = LiveState.Loading))
        assertCardShown()
    }

    /** TalkBack: the banner is one element carrying its own copy, the button is "Start a party, button". */
    @Test
    fun cardIsAnnouncedByItsCopyAndButton() {
        show(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf())))
        compose.onNodeWithTag(SplitPartyCardTestTags.IMAGE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(BANNER_COPY)))
        compose.onNodeWithTag(SplitPartyCardTestTags.BUTTON, useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("Start a party"))
    }

    @Test
    fun buttonAndImageBothStartAParty() {
        show(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf())))
        compose.onNodeWithText("Start a party").performClick()
        compose.onNodeWithTag(SplitPartyCardTestTags.IMAGE).performClick()
        assertEquals(2, partyStarts)
    }

    private companion object {
        const val BANNER_COPY = "Split the check with friends. A faster, easier way to take care of the bill."
    }
}
