package com.splitparty.android.ui.party

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0
    private val annie = MemberUi("annie", "Annie", "avatar_annie", isHost = true)
    private val maxi = MemberUi("maxi", "Maxi", "avatar_maxi", isHost = false)
    private val open = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺"),
        members = persistentListOf(annie),
        millisLeft = 300_000
    )

    private fun show(state: PartyUiState) = compose.setContent {
        SplitPartyTheme { PartyScreen(state, PaddingValues(), onBack = { backs++ }) }
    }

    @Test
    fun hostAloneSeesCodeCountdownBadgeTwoEmptySeatsAndWaitingForTotal() {
        show(open)
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.CODE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Party code: pizza, beer")))
        compose.onNodeWithText("Expires in 5:00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Annie, host").assertIsDisplayed()
        compose.onAllNodesWithTag(PartyTestTags.EMPTY_SEAT).assertCountEquals(2)
        compose.onNodeWithText("Waiting for the total").assertExists()
        compose.onNodeWithText("Pay").assertDoesNotExist()
    }

    @Test
    fun threeMembersFillTheSeats() {
        show(open.copy(members = persistentListOf(annie, maxi, MemberUi("christophe", "Christophe", "avatar_christophe", false))))
        compose.onAllNodesWithTag(PartyTestTags.EMPTY_SEAT).assertCountEquals(0)
        compose.onNodeWithContentDescription("Maxi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Christophe").assertIsDisplayed()
    }

    @Test
    fun reconnectingIndicator() {
        show(open.copy(reconnecting = true))
        compose.onNodeWithText("Reconnecting…").assertIsDisplayed()
    }

    private var cancels = 0
    private val countingActions = object : TotalActions {
        override fun scan() = Unit

        override fun enterTotal() = Unit

        override fun selectTip(choice: TipChoice) = Unit

        override fun customTip() = Unit

        override fun cancel() {
            cancels++
        }
    }

    private fun showWithActions(state: PartyUiState) = compose.setContent {
        SplitPartyTheme { PartyScreen(state, PaddingValues(), onBack = { backs++ }, actions = countingActions) }
    }

    @Test
    fun hostCancelsOnlyAfterConfirmingAndKeepPartyChangesNothing() {
        showWithActions(open.copy(isHost = true, canCancel = true))
        compose.onNodeWithContentDescription("Cancel party. Ends the party for everyone. Nobody is charged.")
            .performScrollTo().performClick()
        compose.onNodeWithText("Cancel party?").assertIsDisplayed()
        compose.onNodeWithText("Keep party").performClick()
        compose.onNodeWithText("Cancel party?").assertDoesNotExist()
        assertEquals(0, cancels)

        compose.onNodeWithTag(PartyTestTags.CANCEL).performScrollTo().performClick()
        compose.onAllNodesWithText("Cancel party").filterToOne(hasClickAction() and !hasTestTag(PartyTestTags.CANCEL))
            .performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun joineesNeverSeeTheCancelControl() {
        show(open.copy(members = persistentListOf(annie, maxi), isHost = false, canCancel = false))
        compose.onNodeWithTag(PartyTestTags.CANCEL).assertDoesNotExist()
        compose.onNodeWithText("Cancel party").assertDoesNotExist()
    }

    @Test
    fun cancelledStateHasItsOwnWordingAndIsAnnounced() {
        show(open.copy(phase = PartyPhase.Cancelled, members = persistentListOf(annie, maxi)))
        compose.onNodeWithText("Party cancelled by host").assertIsDisplayed()
        compose.onNodeWithText("Nobody was charged.").assertIsDisplayed()
        compose.onNodeWithText("This party has expired").assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.TOTAL).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.CANCELLED)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Party cancelled by host"))
        compose.onNodeWithText("Back to Home").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun theHostSeesTheyCancelledIt() {
        show(open.copy(phase = PartyPhase.Cancelled, isHost = true))
        compose.onNodeWithText("You cancelled this party").assertIsDisplayed()
    }

    @Test
    fun expiredStateLeadsBackHome() {
        show(open.copy(phase = PartyPhase.Expired, millisLeft = 0))
        compose.onNodeWithText("This party has expired").assertIsDisplayed()
        compose.onNodeWithText("Back to Home").performClick()
        compose.onNodeWithTag(PartyTestTags.BACK).performClick()
        assertEquals(2, backs)
    }
}
