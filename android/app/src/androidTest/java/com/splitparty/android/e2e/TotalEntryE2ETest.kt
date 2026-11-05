package com.splitparty.android.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.ui.party.EntryTestTags
import com.splitparty.android.ui.party.PartyTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * On the device: the host types the total on the keypad, picks a tip, and
 * system back from the keypad writes nothing (FR-3, FR-4).
 */
@RunWith(AndroidJUnit4::class)
class TotalEntryE2ETest {
    private val harness = PartyHarness(userId = "maxi")
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(harness).around(compose)

    private fun openAsHost(billCents: Long? = null, tip: Tip? = null) {
        harness.publish(harness.party(billCents = billCents, tip = tip))
        compose.launchShell("maxi")
        compose.awaitTag(PartyTestTags.TOTAL)
    }

    private fun confirm() = compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()

    @Test
    fun typing48_50AndConfirmingWrites4850AndEveryFigureUpdates() {
        openAsHost()
        compose.onNodeWithText("Add the total").performScrollTo().assertIsDisplayed()
        compose.tapTag(PartyTestTags.ENTER)
        compose.type("48.50")
        confirm()

        compose.awaitDescription("Your share, 16 dollars and 18 cents").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("bill:4850"), harness.parties.writes)
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Total, 48 dollars and 50 cents")).assertExists()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("3 people")).assertExists()
    }

    @Test
    fun zeroTooBigAndThreeDecimalsAreBlockedAndTheBoundsAreAccepted() {
        openAsHost()
        compose.tapTag(PartyTestTags.ENTER)
        for ((input, message) in listOf(
            "0" to "The amount must be at least $0.01.",
            "10000.00" to "The amount can't be more than $9,999.99.",
            "12.345" to "Use at most two decimals, like 48.50."
        )) {
            compose.type(input)
            confirm()
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.clearAmount(input.length)
        }
        assertTrue(harness.parties.writes.isEmpty())

        compose.type("0.01")
        confirm()
        compose.awaitTag(PartyTestTags.SHARE)
        compose.tapTag(PartyTestTags.ENTER)
        compose.clearAmount()
        compose.type("9999.99")
        confirm()
        compose.awaitDescription("Total, 9999 dollars and 99 cents")
        assertEquals(listOf("bill:1", "bill:999999"), harness.parties.writes)
    }

    @Test
    fun systemBackFromTheKeypadReturnsToThePartyWithNothingWritten() {
        openAsHost()
        compose.tapTag(PartyTestTags.ENTER)
        compose.type("48.50")
        Espresso.pressBack()

        compose.onNodeWithText("Add the total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(EntryTestTags.CONFIRM).assertDoesNotExist()
        assertTrue(harness.parties.writes.isEmpty())
    }

    @Test
    fun eighteenPercentOn100ForThreeGivesTheHost39_34() {
        openAsHost(billCents = 10_000, tip = Tip.ZERO)
        compose.tapTag(PartyTestTags.tipChip("18%"))

        compose.awaitDescription("Your share, 39 dollars and 34 cents").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.tipChip("18%")).assertIsSelected()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Total, 118 dollars")).assertExists()
        assertEquals(listOf("tip:${Tip.Percent(18)}"), harness.parties.writes)
    }

    @Test
    fun customSevenDollarTipIsWrittenAsAnAmount() {
        openAsHost(billCents = 10_000, tip = Tip.Percent(18))
        compose.tapTag(PartyTestTags.tipChip("Custom"))
        compose.type("7")
        confirm()

        compose.awaitDescription("Your share, 35 dollars and 68 cents").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.tipChip("$7.00")).assertIsSelected()
        assertEquals(listOf("tip:${Tip.Custom(700)}"), harness.parties.writes)
    }
}
