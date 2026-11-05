package com.splitparty.android.ui.party

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The confirmation step: a scanned amount is written only on Confirm; back or Edit write nothing. */
// A phone-sized screen (Pixel 5, as the screenshots): the keypad needs real height.
@Config(qualifiers = "w393dp-h851dp")
@RunWith(AndroidJUnit4::class)
class ConfirmScanTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val steps = mutableListOf<TotalStep?>()
    private val confirmed = mutableListOf<Long>()

    private fun show(step: TotalStep) = compose.setContent {
        SplitPartyTheme {
            TotalEntryOverlay(
                step = step,
                state = PartyUiState(phase = PartyPhase.Open, isHost = true),
                contentPadding = PaddingValues(),
                onStep = { steps += it },
                onConfirmBill = { confirmed += it },
                onConfirmTip = {},
                scanScreen = {}
            )
        }
    }

    @Test
    fun showsTheAmountAndItsLineAndConfirmWritesIt() {
        show(TotalStep.ConfirmScan(5_371, "Total"))
        compose.onNodeWithContentDescription("53 dollars and 71 cents").assertIsDisplayed()
        compose.onNodeWithText("From the line \"Total\"").assertIsDisplayed()
        compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()
        assertEquals(listOf(5_371L), confirmed)
    }

    @Test
    fun systemBackReturnsToThePartyWithNothingWritten() {
        show(TotalStep.ConfirmScan(5_371, "Total"))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf<TotalStep?>(null), steps)
        assertTrue(confirmed.isEmpty())
    }

    @Test
    fun editOpensTheKeypadPrefilled() {
        show(TotalStep.ConfirmScan(5_371, "Total"))
        compose.onNodeWithTag(EntryTestTags.EDIT).performClick()
        assertEquals(listOf<TotalStep?>(TotalStep.EnterBill(prefillCents = 5_371)), steps)
        assertTrue(confirmed.isEmpty())
    }

    @Test
    fun noTotalFoundExplainsOnTheKeypad() {
        show(TotalStep.EnterBill(notice = EntryNotice.NoTotalFound))
        compose.onNodeWithText("We couldn't find a total on the receipt. Type it in instead.").assertIsDisplayed()
        compose.onNodeWithTag(EntryTestTags.key("1")).assertIsDisplayed()
    }

    @Test
    fun theStepSurvivesBeingSaved() {
        for (step in listOf(
            TotalStep.EnterBill(4_850, EntryNotice.NoTotalFound),
            TotalStep.EnterTip(700),
            TotalStep.Scan,
            TotalStep.ConfirmScan(5_371, "TOTAL | $53.71")
        )) {
            val saved = with(TotalStep.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(step) }
            assertEquals(step, TotalStep.Saver.restore(saved!!))
        }
    }
}
