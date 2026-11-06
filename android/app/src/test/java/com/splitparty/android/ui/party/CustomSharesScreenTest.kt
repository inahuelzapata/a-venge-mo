package com.splitparty.android.ui.party

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.split.CustomShareProblem
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shares list: who gets controls, what TalkBack reads, and the keypad for one member's amount. */
@RunWith(AndroidJUnit4::class)
class CustomSharesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val edits = mutableListOf<String>()
    private val actions = object : TotalActions {
        override fun scan() = Unit

        override fun enterTotal() = Unit

        override fun selectTip(choice: TipChoice) = Unit

        override fun customTip() = Unit

        override fun editShare(userId: String) {
            edits += userId
        }
    }

    private val shares = persistentListOf(
        MemberShareUi("maxi", "Maxi", isHost = true, isMe = true, customCents = 1_000, shareCents = 1_000),
        MemberShareUi("annie", "Annie", isHost = false, isMe = false, customCents = 3_500, shareCents = 3_500),
        MemberShareUi("christophe", "Christophe", isHost = false, isMe = false, customCents = null, shareCents = 1_500)
    )
    private val hostState = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺"),
        members = persistentListOf(MemberUi("maxi", "Maxi", null, isHost = true)),
        millisLeft = 300_000,
        isHost = true,
        hostName = "Maxi",
        total = TotalUi(6_000, TipChoice.Zero, 0, 6_000, 3, 1_000),
        pay = PayState.Ready,
        shares = shares,
        canEditShares = true
    )

    private fun show(state: PartyUiState) = compose.setContent {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            SplitPartyTheme { PartyScreen(state, PaddingValues(), onBack = {}, actions = actions) }
        }
    }

    private fun spoken(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    private val tappable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
    private val notTappable = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)

    @Test
    fun hostWithTheSwitchOnTapsAShareToEditItAndTalkBackReadsNameAmountAndKind() {
        show(hostState)
        compose.onNodeWithTag(PartyTestTags.share("annie")).performScrollTo()
            .assert(spoken("Annie, custom share, 35 dollars"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
            .performClick()
        compose.onNodeWithTag(PartyTestTags.share("christophe")).performScrollTo()
            .assert(spoken("Christophe, remainder share, 15 dollars"))
        compose.onNodeWithTag(PartyTestTags.share("maxi")).performScrollTo()
            .assert(spoken("Maxi (you), custom share, 10 dollars"))
            .performClick()
        assertEquals(listOf("annie", "maxi"), edits)
    }

    @Test
    fun withoutControlsTheSharesAreStillListedButTappingDoesNothing() {
        // A host with the switch off, or any joinee: same list, no controls.
        show(hostState.copy(canEditShares = false))
        compose.onNodeWithTag(PartyTestTags.share("annie")).performScrollTo()
            .assert(spoken("Annie, custom share, 35 dollars"))
            .assert(notTappable)
        compose.onNodeWithTag(PartyTestTags.share("christophe")).assert(notTappable)
    }

    @Test
    fun joineeWithoutCustomAmountsSeesNoSharesList() {
        show(
            hostState.copy(
                isHost = false,
                pay = null,
                canEditShares = false,
                shares = shares.map {
                    it.copy(customCents = null)
                }.let { persistentListOf(*it.toTypedArray()) }
            )
        )
        compose.onNodeWithTag(PartyTestTags.SHARES).assertDoesNotExist()
    }

    @Test
    fun amountsOverTheTotalTellTheHostAndDisableCollectWithTheReason() {
        show(
            hostState.copy(
                total = TotalUi(4_000, TipChoice.Zero, 0, 4_000, 3, 1_000),
                pay = PayState.SharesDontFit,
                shares = persistentListOf(shares[0], shares[1], shares[2].copy(shareCents = null)),
                sharesProblem = SharesProblemUi(CustomShareProblem.OverTotal, 4_500, 4_000)
            )
        )
        compose.onNodeWithTag(PartyTestTags.SHARES_PROBLEM).performScrollTo()
        compose.onNodeWithTag(PartyTestTags.share("christophe"))
            .assert(spoken("Christophe, remainder share, waiting for the custom amounts to fit"))
        compose.onNodeWithTag(PartyTestTags.PAY).performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        assert(tappable.matches(compose.onNodeWithTag(PartyTestTags.share("annie")).fetchSemanticsNode()))
    }

    @Test
    fun theShareKeypadConfirmsAnAmountIncludingZeroAndClearsBackToTheEqualSplit() {
        val confirmed = mutableListOf<Pair<String, Long?>>()
        compose.setContent {
            SplitPartyTheme {
                TotalEntryOverlay(
                    step = TotalStep.EnterShare("annie", prefillCents = 3_500),
                    state = hostState,
                    contentPadding = PaddingValues(),
                    onStep = {},
                    onConfirmBill = {},
                    onConfirmTip = {},
                    scanScreen = {},
                    onConfirmShare = { id, cents -> confirmed += id to cents }
                )
            }
        }
        compose.onNodeWithText("Annie's share").assertExists()
        compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()
        compose.onNodeWithTag(EntryTestTags.CLEAR).performClick()
        repeat(7) { compose.onNodeWithTag(EntryTestTags.key(KEY_DELETE)).performClick() }
        compose.onNodeWithTag(EntryTestTags.key("0")).performClick()
        compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()
        assertEquals(listOf<Pair<String, Long?>>("annie" to 3_500L, "annie" to null, "annie" to 0L), confirmed)
    }

    @Test
    fun theShareStepSurvivesBeingSaved() {
        val saver = TotalStep.Saver
        val step = TotalStep.EnterShare("christophe", 1_500)
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(step) }
        assertEquals(step, saver.restore(saved!!))
    }
}
