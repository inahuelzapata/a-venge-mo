package com.splitparty.android.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.ui.party.PartyTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** On a member's phone: no controls, then the live share as the host's changes and joins arrive. */
@RunWith(AndroidJUnit4::class)
class MemberShareE2ETest {
    private val harness = PartyHarness(userId = "annie")
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(harness).around(compose)

    @Test
    fun waitsWithoutControlsThenFollowsEveryChangeLive() {
        harness.publish(harness.party(members = listOf("maxi", "annie")))
        compose.launchShell("annie")
        compose.awaitTag(PartyTestTags.TOTAL)
        compose.onNodeWithText("Waiting for the total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.ENTER).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.SCAN).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.TIP_CHIPS).assertDoesNotExist()

        // The host confirms 100.00 with 18%: two people.
        harness.publish(harness.party(members = listOf("maxi", "annie"), billCents = 10_000, tip = Tip.Percent(18)))
        compose.awaitDescription("Your share, 59 dollars").performScrollTo().assertIsDisplayed()
        compose.onNode(hasContentDescription("Tip, 18%, 18 dollars")).assertExists()
        compose.onNodeWithTag(PartyTestTags.TIP_CHIPS).assertDoesNotExist()

        // Christophe joins: the share and headcount change together.
        harness.publish(harness.party(billCents = 10_000, tip = Tip.Percent(18)))
        compose.awaitDescription("Your share, 39 dollars and 33 cents")
        compose.onNode(hasContentDescription("3 people")).assertExists()

        // The host re-enters 200.00.
        harness.publish(harness.party(billCents = 20_000, tip = Tip.Percent(18)))
        compose.awaitDescription("Your share, 78 dollars and 66 cents")
        compose.onNode(hasContentDescription("Total, 236 dollars")).assertExists()
    }
}
