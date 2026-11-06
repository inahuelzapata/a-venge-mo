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
import com.splitparty.android.domain.party.BannerSlot
import com.splitparty.android.testing.TestData
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var partyStarts = 0
    private val joins = mutableListOf<String>()
    private val opened = mutableListOf<String>()

    private fun show(state: HomeUiState) = compose.setContent {
        SplitPartyTheme {
            HomeScreen(
                state,
                contentPadding = PaddingValues(),
                onStartParty = { partyStarts++ },
                onJoin = { joins += it },
                onOpenParty = { opened += it }
            )
        }
    }

    private val users = TestData.everyone.associateBy { it.id }.toImmutableMap()

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

    @Test
    fun demoSwitchOffShowsNoCardAndTheFeedRightBelowTheHeader() {
        show(HomeUiState("Maxi", feed = LiveState.Ready(PreviewData.transactions), users = PreviewData.users, bannerSlot = BannerSlot.None))
        compose.onNodeWithText("Start a party").assertDoesNotExist()
        compose.onNodeWithTag(PartyBannerTestTags.JOIN).assertDoesNotExist()
        compose.onNodeWithText("Annie paid", substring = true).assertIsDisplayed()
    }

    /** TalkBack: "Annie started a Split Party at Tony's, Join", one button. */
    @Test
    fun joinBannerNamesHostAndMerchantAndJoins() {
        val party = TestData.party(id = "p1", host = "annie")
        show(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf()), users = users, bannerSlot = BannerSlot.Join(party)))
        compose.onNodeWithText("Start a party").assertDoesNotExist()
        compose.onNodeWithTag(PartyBannerTestTags.JOIN)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Annie started a Split Party at Tony's, Join"))
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(listOf("p1"), joins)
    }

    @Test
    fun memberSeesBackToYourParty() {
        val party = TestData.party(id = "p1", host = "annie", members = listOf("annie", "maxi"))
        show(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf()), users = users, bannerSlot = BannerSlot.BackToParty(party)))
        compose.onNodeWithText("Start a party").assertDoesNotExist()
        compose.onNodeWithText("Back to your party").performClick()
        assertEquals(listOf("p1"), opened)
    }

    @Test
    fun failedStartShowsTheInlineError() {
        show(HomeUiState("Annie", feed = LiveState.Ready(persistentListOf()), startPartyFailed = true))
        compose.onNodeWithText("Couldn't start the party", substring = true).assertIsDisplayed()
    }

    private companion object {
        const val BANNER_COPY = "Tony's at the J House. Answer a trivia and get a 10% discount."
    }
}
