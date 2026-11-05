package com.splitparty.android.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.ui.home.PartyBannerTestTags
import com.splitparty.android.ui.party.PartyTestTags
import com.splitparty.android.ui.shell.ShellTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** FR-2.5 on the device: the host removes a member, a member is removed, a member leaves. */
@RunWith(AndroidJUnit4::class)
class HostRemovesMemberE2ETest {
    private val harness = PartyHarness(userId = "maxi")
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(harness).around(compose)

    @Test
    fun theHostRemovesChristopheAfterConfirmingAndSharesRecompute() {
        harness.publish(harness.party(billCents = 10_000, tip = Tip.Percent(18)))
        compose.launchShell("maxi")
        compose.awaitDescription("Your share, 39 dollars and 34 cents")
        compose.onNodeWithTag(PartyTestTags.seat("maxi")).assertHasNoClickAction()
        compose.onNodeWithTag(PartyTestTags.LEAVE).assertDoesNotExist()

        compose.onNodeWithTag(PartyTestTags.seat("christophe")).performClick()
        compose.onNodeWithText("Remove Christophe?").assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()

        compose.awaitDescription("Your share, 59 dollars")
        compose.onNodeWithTag(PartyTestTags.seat("christophe")).assertDoesNotExist()
        assertEquals(listOf("remove:christophe"), harness.parties.writes)
    }
}

@RunWith(AndroidJUnit4::class)
class RemovedMemberE2ETest {
    private val harness = PartyHarness(userId = "christophe")
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(harness).around(compose)

    @Test
    fun theRemovedPhoneGoesHomeWithTheMessageAndCanRejoin() {
        harness.publish(harness.party(billCents = 10_000, tip = Tip.Percent(18)))
        compose.launchShell("christophe")
        compose.awaitDescription("Your share, 39 dollars and 33 cents")

        harness.publish(harness.party(members = listOf("maxi", "annie"), billCents = 10_000, tip = Tip.Percent(18)))
        compose.awaitTag(ShellTestTags.NOTICE).assertIsDisplayed()
        compose.onNodeWithText("You were removed from Maxi's party").assertIsDisplayed()
        compose.onNodeWithText("Split Party at Tony's").assertDoesNotExist()
        // Not locked out: the Join banner is back, as for a first-time joiner.
        compose.awaitTag(PartyBannerTestTags.JOIN).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class MemberLeavesE2ETest {
    private val harness = PartyHarness(userId = "annie")
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(harness).around(compose)

    @Test
    fun leavingAfterConfirmingGoesHomeWithTheJoinBannerAndNoMessage() {
        harness.publish(harness.party())
        compose.launchShell("annie")
        compose.tapTag(PartyTestTags.LEAVE)
        compose.onNodeWithText("Leave Maxi's party?").assertIsDisplayed()
        compose.onNodeWithText("Stay").performClick()
        compose.onNodeWithTag(PartyTestTags.DIALOG).assertDoesNotExist()

        compose.tapTag(PartyTestTags.LEAVE)
        // What the server will show once the leave lands.
        harness.parties.open.value = com.splitparty.android.testing.TestData.server(
            listOf(harness.party(members = listOf("maxi", "christophe")))
        )
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()

        compose.awaitTag(PartyBannerTestTags.JOIN).assertIsDisplayed()
        compose.onNodeWithTag(ShellTestTags.NOTICE).assertDoesNotExist()
        assertEquals(listOf("leave:annie"), harness.parties.writes)
    }
}
