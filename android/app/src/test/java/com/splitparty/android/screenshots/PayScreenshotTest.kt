package com.splitparty.android.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.model.Party
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.me.DemoResetUi
import com.splitparty.android.ui.me.MeScreen
import com.splitparty.android.ui.me.MeUiState
import com.splitparty.android.ui.party.MemberUi
import com.splitparty.android.ui.party.PartyHeader
import com.splitparty.android.ui.party.PartyPhase
import com.splitparty.android.ui.party.PartyScreen
import com.splitparty.android.ui.party.PartyUiState
import com.splitparty.android.ui.party.PayState
import com.splitparty.android.ui.party.SettlementUi
import com.splitparty.android.ui.party.TipChoice
import com.splitparty.android.ui.party.TotalActions
import com.splitparty.android.ui.party.TotalArea
import com.splitparty.android.ui.party.TotalUi
import com.splitparty.android.ui.shell.ShellFrame
import com.splitparty.android.ui.shell.Tab
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.topInset
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

/**
 * Pay, settlement and Demo reset (PoC-11, PoC-12), Android side. Provisional:
 * no iOS captures of these states exist yet; the side-by-side comparisons are
 * made once they do (docs/design/visual-conventions.md, "Pay and settlement").
 * Rendered with animations removed, i.e. the settled end state.
 */
class PayScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule(fullResolution = true)

    private val annie = MemberUi("annie", "Annie", "avatar_annie", isHost = true)
    private val maxi = MemberUi("maxi", "Maxi", "avatar_maxi", isHost = false)
    private val christophe = MemberUi("christophe", "Christophe", "avatar_christophe", isHost = false)

    /** Annie hosts; 100.00 + 18% for three: Annie 39.34, Maxi and Christophe 39.33. */
    private val host = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺", "🎉"),
        members = persistentListOf(annie, maxi, christophe),
        millisLeft = Party.TTL_MILLIS,
        isHost = true,
        hostName = "Annie",
        total = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 3, 3_934),
        pay = PayState.Ready
    )

    private val settled = host.copy(
        phase = PartyPhase.Settled,
        members = persistentListOf(annie.copy(paid = true), maxi.copy(paid = true), christophe.copy(paid = true))
    )

    @Test
    fun payEnabled() = totalOnly("pay-enabled", host)

    @Test
    fun payDisabledNeedsTotal() = totalOnly("pay-disabled-total", host.copy(total = null, pay = PayState.NeedsTotal))

    @Test
    fun payDisabledNeedsMembers() = totalOnly(
        "pay-disabled-members",
        host.copy(
            members = persistentListOf(annie),
            total = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 1, 11_800),
            pay = PayState.NeedsMembers
        )
    )

    @Test
    fun settledHost() = party("settled-host", settled.copy(settlement = SettlementUi(isHost = true, amountCents = 7_866, animate = false)))

    @Test
    fun settledMember() = party(
        "settled-member",
        settled.copy(isHost = false, pay = null, settlement = SettlementUi(isHost = false, amountCents = 3_933, animate = false))
    )

    @Test
    fun meDemoSection() = paparazzi.screen("me-demo-section") {
        ShellFrame(current = Tab.Me, onSelect = {}) { padding ->
            MeScreen(
                MeUiState(
                    userId = "annie",
                    displayName = "Annie",
                    avatar = "avatar_annie",
                    profile = LiveState.Ready(PreviewData.maxi),
                    transactions = LiveState.Ready(persistentListOf()),
                    users = PreviewData.users,
                    demoAtMerchant = true,
                    reset = DemoResetUi.Failed
                ),
                contentPadding = padding,
                onAction = {},
                // Scrolled to "Your payments", so the Demo section at the bottom of the tab is in view.
                listState = rememberLazyListState(initialFirstVisibleItemIndex = 3)
            )
        }
    }

    private fun party(name: String, state: PartyUiState) = shell(name) { padding -> PartyScreen(state, padding, onBack = {}) }

    private fun totalOnly(name: String, state: PartyUiState) = shell(name) { padding ->
        Column(Modifier.fillMaxSize().padding(top = topInset() + Dimens.HeaderTop, bottom = padding.calculateBottomPadding())) {
            PartyHeader("Split Party at Tony's", onBack = {})
            Spacer(Modifier.height(Dimens.ContentSpacing))
            TotalArea(state, TotalActions.None)
        }
    }

    private fun shell(name: String, content: @Composable (PaddingValues) -> Unit) =
        paparazzi.screen(name) { ShellFrame(current = Tab.Home, onSelect = {}, content = content) }
}

/** System theme dark: the app is light-only, so these must match their light renders. */
class PayDarkScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = SnapshotDevice.copy(nightMode = NightMode.NIGHT),
        useDeviceResolution = true,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.1
    )

    @Test
    fun settledHostInDarkMode() = paparazzi.screen("settled-host-dark") {
        ShellFrame(current = Tab.Home, onSelect = {}) { padding ->
            PartyScreen(
                PartyUiState(
                    phase = PartyPhase.Settled,
                    members = persistentListOf(
                        MemberUi("annie", "Annie", "avatar_annie", isHost = true, paid = true),
                        MemberUi("maxi", "Maxi", "avatar_maxi", isHost = false, paid = true)
                    ),
                    isHost = true,
                    hostName = "Annie",
                    settlement = SettlementUi(isHost = true, amountCents = 5_900, animate = false)
                ),
                padding,
                onBack = {}
            )
        }
    }
}
