package com.splitparty.android.screenshots

import androidx.compose.foundation.layout.PaddingValues
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.me.MeScreen
import com.splitparty.android.ui.me.MeUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class MeScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule()

    private val maxi = MeUiState(userId = "maxi", displayName = "Maxi", avatar = "avatar_maxi", users = PreviewData.users)

    private fun me(state: MeUiState) = paparazzi.screen { MeScreen(state, contentPadding = PaddingValues(), onAction = {}) }

    @Test
    fun loaded() = me(maxi.copy(profile = LiveState.Ready(PreviewData.maxi), transactions = LiveState.Ready(PreviewData.transactions)))

    @Test
    fun noPaymentsYet() = me(maxi.copy(profile = LiveState.Ready(PreviewData.maxi), transactions = LiveState.Ready(persistentListOf())))

    @Test
    fun loading() = me(maxi)

    @Test
    fun accountNotFound() = me(maxi.copy(profile = LiveState.Ready(null), transactions = LiveState.Ready(persistentListOf())))

    @Test
    fun offline() = me(maxi.copy(profile = LiveState.Unreachable(OFFLINE_MESSAGE), transactions = LiveState.Unreachable(OFFLINE_MESSAGE)))
}
