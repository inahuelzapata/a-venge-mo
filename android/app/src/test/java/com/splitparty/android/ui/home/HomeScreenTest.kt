package com.splitparty.android.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: HomeUiState) = compose.setContent {
        SplitPartyTheme { HomeScreen(state, contentPadding = PaddingValues()) }
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
    }

    @Test
    fun unreachableShowsTheErrorCard() {
        show(HomeUiState("Maxi", feed = LiveState.Unreachable(OFFLINE_MESSAGE)))
        compose.onNodeWithText("Can't load payments").assertIsDisplayed()
        compose.onNodeWithText(OFFLINE_MESSAGE).assertIsDisplayed()
    }
}
