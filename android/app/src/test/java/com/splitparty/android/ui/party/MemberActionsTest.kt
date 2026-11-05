package com.splitparty.android.ui.party

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.robolectric.annotation.Config

/** FR-2.5: the host removes a member after a confirmation; a member leaves after one. */
@Config(qualifiers = "w393dp-h851dp")
@RunWith(AndroidJUnit4::class)
class MemberActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : TotalActions {
        override fun scan() = Unit

        override fun enterTotal() = Unit

        override fun selectTip(choice: TipChoice) = Unit

        override fun customTip() = Unit

        override fun removeMember(userId: String) {
            calls += "remove:$userId"
        }

        override fun leave() {
            calls += "leave"
        }
    }

    private val state = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺"),
        members = persistentListOf(
            MemberUi("maxi", "Maxi", null, isHost = true),
            MemberUi("annie", "Annie", null, isHost = false),
            MemberUi("christophe", "Christophe", null, isHost = false)
        ),
        millisLeft = 300_000,
        hostName = "Maxi"
    )

    private fun show(state: PartyUiState) = compose.setContent {
        SplitPartyTheme { PartyScreen(state, PaddingValues(), onBack = {}, actions = actions) }
    }

    @Test
    fun hostRemovesAMemberAfterConfirmingAndCannotRemoveThemself() {
        show(state.copy(isHost = true))
        compose.onNodeWithTag(PartyTestTags.seat("maxi")).assertHasNoClickAction()
        compose.onNodeWithTag(PartyTestTags.LEAVE).assertDoesNotExist()

        compose.onNodeWithTag(PartyTestTags.seat("christophe")).assertHasClickAction().performClick()
        compose.onNodeWithText("Remove Christophe?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithTag(PartyTestTags.seat("christophe")).performClick()
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()
        assertEquals(listOf("remove:christophe"), calls)
        compose.onNodeWithTag(PartyTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun memberSeesLeaveWithAConfirmationAndCannotRemoveAnyone() {
        show(state)
        compose.onNodeWithTag(PartyTestTags.seat("christophe")).assertHasNoClickAction()
        compose.onNodeWithTag(PartyTestTags.LEAVE).performScrollTo().performClick()
        compose.onNodeWithText("Leave Maxi's party?").assertExists()
        compose.onNodeWithText("Stay").performClick()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithTag(PartyTestTags.LEAVE).performScrollTo().performClick()
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()
        assertEquals(listOf("leave"), calls)
    }
}
