package com.splitparty.android.screenshots

import androidx.compose.foundation.layout.PaddingValues
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.domain.live.UNREACHABLE_MESSAGE
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.home.HomeScreen
import com.splitparty.android.ui.home.HomeUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test

class HomeScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule()

    private fun home(state: HomeUiState) = paparazzi.screen { HomeScreen(state, contentPadding = PaddingValues()) }

    @Test
    fun loaded() = home(HomeUiState("Maxi", feed = LiveState.Ready(PreviewData.transactions), users = PreviewData.users))

    /** A row that just arrived, at the start of its highlight. */
    @Test
    fun newRowHighlighted() = home(
        HomeUiState(
            currentUserName = "Maxi",
            feed = LiveState.Ready(PreviewData.transactions),
            users = PreviewData.users,
            highlighted = persistentSetOf(PreviewData.transactions.first().id)
        )
    )

    /** Names fall back to the capitalised id until `users` loads. */
    @Test
    fun usersNotLoadedYet() = home(HomeUiState("Maxi", feed = LiveState.Ready(PreviewData.transactions)))

    @Test
    fun loading() = home(HomeUiState("Maxi"))

    @Test
    fun empty() = home(HomeUiState("Maxi", feed = LiveState.Ready(persistentListOf())))

    @Test
    fun offline() = home(HomeUiState("Maxi", feed = LiveState.Unreachable(OFFLINE_MESSAGE)))

    @Test
    fun unreachable() = home(HomeUiState("Maxi", feed = LiveState.Unreachable(UNREACHABLE_MESSAGE)))
}
