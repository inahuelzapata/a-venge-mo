package com.splitparty.android.ui.party

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TotalAreaTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : TotalActions {
        override fun scan() {
            calls += "scan"
        }

        override fun enterTotal() {
            calls += "enter"
        }

        override fun selectTip(choice: TipChoice) {
            calls += "tip:$choice"
        }

        override fun customTip() {
            calls += "custom"
        }
    }

    private val open = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺"),
        members = persistentListOf(MemberUi("maxi", "Maxi", null, isHost = true)),
        millisLeft = 300_000,
        hostName = "Maxi"
    )
    private val split = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 3, 3_933)

    private fun show(state: PartyUiState) = compose.setContent {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            SplitPartyTheme { PartyScreen(state, PaddingValues(), onBack = {}, actions = actions) }
        }
    }

    private fun spoken(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    @Test
    fun hostWithoutBillSeesAddTheTotalWithScanAndEnter() {
        show(open.copy(isHost = true))
        compose.onNodeWithText("Add the total").performScrollTo()
        compose.onNodeWithTag(PartyTestTags.SCAN).performScrollTo().performClick()
        compose.onNodeWithTag(PartyTestTags.ENTER).performScrollTo().performClick()
        assertEquals(listOf("scan", "enter"), calls)
        compose.onNodeWithText("Waiting for the total").assertDoesNotExist()
    }

    @Test
    fun memberWithoutBillSeesWaitingAndNoControls() {
        show(open)
        compose.onNodeWithText("Waiting for the total").performScrollTo()
        compose.onNodeWithTag(PartyTestTags.SCAN).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.ENTER).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.TIP_CHIPS).assertDoesNotExist()
    }

    @Test
    fun memberWithBillReadsSpokenAmountsAndHasNoChips() {
        show(open.copy(total = split))
        compose.onNodeWithContentDescription("Your share, 39 dollars and 33 cents").performScrollTo()
        compose.onNodeWithContentDescription("Your share, 39 dollars and 33 cents").assertExists()
        compose.onNodeWithTag(PartyTestTags.TOTAL_VALUE).assert(spoken("Total, 118 dollars"))
        compose.onNodeWithTag(PartyTestTags.HEADCOUNT).assert(spoken("3 people"))
        compose.onNodeWithContentDescription("Tip, 18%, 18 dollars").assertExists()
        compose.onNodeWithTag(PartyTestTags.TIP_CHIPS).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.SCAN).assertDoesNotExist()
    }

    @Test
    fun hostChipsAreASelectableGroupWithTheChosenOneSelected() {
        show(open.copy(isHost = true, total = split.copy(shareCents = 3_934)))
        compose.onNodeWithTag(PartyTestTags.tipChip("18%")).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(PartyTestTags.tipChip("0%")).assertIsNotSelected()
        compose.onNodeWithContentDescription("18% tip").assertExists()
        compose.onNodeWithTag(PartyTestTags.tipChip("20%")).performClick()
        compose.onNodeWithTag(PartyTestTags.tipChip("Custom")).performClick()
        assertEquals(listOf("tip:${TipChoice.Percent(20)}", "custom"), calls)
    }

    @Test
    fun customTipChipShowsItsAmount() {
        show(open.copy(isHost = true, total = TotalUi(10_000, TipChoice.Custom(700), 700, 10_700, 3, 3_568)))
        compose.onNodeWithTag(PartyTestTags.tipChip("$7.00")).performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Your share, 35 dollars and 68 cents").assertExists()
    }

    @Test
    fun aFailedWriteShowsInlineForTheHost() {
        show(open.copy(isHost = true, writeError = WriteError.Failed))
        compose.onNodeWithTag(PartyTestTags.WRITE_ERROR).performScrollTo()
        compose.onNodeWithText("Couldn't save. Check your connection and try again.").assertExists()
    }

    @Test
    fun expiredKeepsTheFrozenTotalWithoutControls() {
        show(open.copy(phase = PartyPhase.Expired, isHost = true, total = split))
        compose.onNodeWithText("This party has expired").assertExists()
        compose.onNodeWithContentDescription("Your share, 39 dollars and 33 cents").performScrollTo()
        compose.onNodeWithTag(PartyTestTags.TIP_CHIPS).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.SCAN).assertDoesNotExist()
    }
}
