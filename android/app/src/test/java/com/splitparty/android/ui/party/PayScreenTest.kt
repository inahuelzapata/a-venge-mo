package com.splitparty.android.ui.party

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
class PayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var pays = 0
    private var homes = 0
    private val annie = MemberUi("annie", "Annie", "avatar_annie", isHost = true)
    private val maxi = MemberUi("maxi", "Maxi", "avatar_maxi", isHost = false)
    private val christophe = MemberUi("christophe", "Christophe", "avatar_christophe", isHost = false)
    private val total = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, headcount = 3, shareCents = 3_934)
    private val hostOpen = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺"),
        members = persistentListOf(annie, maxi, christophe),
        millisLeft = 200_000,
        isHost = true,
        hostName = "Annie",
        total = total,
        pay = PayState.Ready
    )

    private fun show(state: PartyUiState, reduceMotion: Boolean = true) = compose.setContent {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            SplitPartyTheme {
                PartyScreen(
                    state,
                    PaddingValues(),
                    onBack = { homes++ },
                    actions = object : TotalActions by TotalActions.None {
                        override fun pay() {
                            pays++
                        }
                    }
                )
            }
        }
    }

    @Test
    fun readyPayCollectsEveryoneElsesShareInOneTap() {
        show(hostOpen)
        compose.onNodeWithTag(PartyTestTags.PAY).performScrollTo().assertIsEnabled()
            .assertContentDescriptionEquals("Collect 78 dollars and 66 cents from everyone")
            .performClick()
        compose.onNodeWithText("Collect · $78.66", useUnmergedTree = true).assertExists()
        assertEquals(1, pays)
    }

    @Test
    fun withoutATotalPayIsDisabledAndSaysWhy() {
        show(hostOpen.copy(total = null, pay = PayState.NeedsTotal))
        compose.onNodeWithTag(PartyTestTags.PAY).performScrollTo().assertIsNotEnabled()
            .assertContentDescriptionEquals("Collect, disabled. Add the total")
            .performClick()
        assertEquals(0, pays)
    }

    @Test
    fun withoutMembersPayIsDisabledAndSaysWhy() {
        show(hostOpen.copy(members = persistentListOf(annie), pay = PayState.NeedsMembers))
        compose.onNodeWithTag(PartyTestTags.PAY).performScrollTo().assertIsNotEnabled()
            .assertContentDescriptionEquals("Collect, disabled. Waiting for friends to join")
    }

    @Test
    fun membersNeverSeeAPayButton() {
        show(hostOpen.copy(isHost = false, pay = null))
        compose.onNodeWithTag(PartyTestTags.PAY).assertDoesNotExist()
    }

    @Test
    fun expiredPartyShowsPayDisabled() {
        show(hostOpen.copy(phase = PartyPhase.Expired, pay = PayState.Expired))
        compose.onNodeWithTag(PartyTestTags.PAY).performScrollTo().assertIsNotEnabled()
            .assertContentDescriptionEquals("Collect, disabled. This party has expired")
    }

    @Test
    fun hostSuccessReadsTheAmountCollectedWithEveryoneTicked() {
        show(
            hostOpen.copy(
                phase = PartyPhase.Settled,
                members = persistentListOf(annie.copy(paid = true), maxi.copy(paid = true), christophe.copy(paid = true)),
                settlement = SettlementUi(isHost = true, amountCents = 7_866, animate = true)
            )
        )
        compose.onNodeWithContentDescription("Maxi, paid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Christophe, paid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settled. You collected 78 dollars and 66 cents").assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.SUCCESS_CHECK).assertExists()
        compose.onNodeWithTag(PartyTestTags.SETTLED_HOME).performScrollTo().performClick()
        assertEquals(1, homes)
    }

    @Test
    fun memberSuccessReadsTheAmountPaid() {
        show(
            hostOpen.copy(
                phase = PartyPhase.Settled,
                isHost = false,
                pay = null,
                settlement = SettlementUi(isHost = false, amountCents = 3_933, animate = false)
            )
        )
        compose.onNodeWithContentDescription("Settled. You paid 39 dollars and 33 cents").assertIsDisplayed()
        compose.onNodeWithText("Paid to Annie for Tony's.").assertExists()
    }
}

private fun SemanticsNodeInteraction.assertContentDescriptionEquals(text: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text)))
