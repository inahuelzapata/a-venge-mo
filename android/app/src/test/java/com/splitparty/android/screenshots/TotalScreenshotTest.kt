package com.splitparty.android.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.splitparty.android.domain.model.Party
import com.splitparty.android.ui.home.HomeScreen
import com.splitparty.android.ui.home.HomeUiState
import com.splitparty.android.ui.party.ConfirmCard
import com.splitparty.android.ui.party.EntryNotice
import com.splitparty.android.ui.party.MemberUi
import com.splitparty.android.ui.party.PartyHeader
import com.splitparty.android.ui.party.PartyPhase
import com.splitparty.android.ui.party.PartyScreen
import com.splitparty.android.ui.party.PartyUiState
import com.splitparty.android.ui.party.TipChoice
import com.splitparty.android.ui.party.TotalActions
import com.splitparty.android.ui.party.TotalArea
import com.splitparty.android.ui.party.TotalEntryOverlay
import com.splitparty.android.ui.party.TotalStep
import com.splitparty.android.ui.party.TotalUi
import com.splitparty.android.ui.scan.ScanLayout
import com.splitparty.android.ui.scan.ScanPhase
import com.splitparty.android.ui.shell.ShellFrame
import com.splitparty.android.ui.shell.ShellNotice
import com.splitparty.android.ui.shell.Tab
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.topInset
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

/**
 * The total/tip/share slice (PoC-10), Android side. Provisional: no iOS
 * captures of these screens exist yet, so the comparisons are made once they
 * do (docs/design/visual-conventions.md, "Total, tip and share").
 */
class TotalScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule(fullResolution = true)

    private val maxi = MemberUi("maxi", "Maxi", "avatar_maxi", isHost = true)
    private val annie = MemberUi("annie", "Annie", "avatar_annie", isHost = false)
    private val christophe = MemberUi("christophe", "Christophe", "avatar_christophe", isHost = false)

    private val party = PartyUiState(
        phase = PartyPhase.Open,
        joinCode = persistentListOf("🍕", "🍺"),
        members = persistentListOf(maxi, annie, christophe),
        millisLeft = Party.TTL_MILLIS,
        hostName = "Maxi"
    )

    /** 100.00 + 18% for three: 118.00, host 39.34, others 39.33. */
    private val split = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 3, 3_933)

    @Test
    fun addTheTotal() = totalOnly("total-add", party.copy(isHost = true))

    @Test
    fun waitingForTheTotal() = totalOnly("total-waiting", party)

    @Test
    fun tipChosen() = totalOnly("total-tip-chosen", party.copy(isHost = true, total = split.copy(shareCents = 3_934)))

    @Test
    fun customTipChosen() = totalOnly(
        "total-tip-custom",
        party.copy(isHost = true, total = TotalUi(10_000, TipChoice.Custom(700), 700, 10_700, 3, 3_568))
    )

    @Test
    fun shareOneMember() = totalOnly("total-share-1", party.copy(total = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 1, 11_800)))

    @Test
    fun shareTwoMembers() = totalOnly("total-share-2", party.copy(total = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 2, 5_900)))

    @Test
    fun shareThreeMembers() = totalOnly("total-share-3", party.copy(total = split))

    @Test
    fun keypad() = overlay("total-keypad", TotalStep.EnterBill(prefillCents = 4_850))

    @Test
    fun confirmation() = overlay("total-confirm", TotalStep.ConfirmScan(5_371, "Total"))

    @Test
    fun noTotalFound() = overlay("total-no-total-found", TotalStep.EnterBill(notice = EntryNotice.NoTotalFound))

    @Test
    fun cameraDenied() = overlay("total-camera-denied", TotalStep.EnterBill(notice = EntryNotice.CameraDenied))

    @Test
    fun scanning() = shell("total-scanning") { padding ->
        // The laser line shows (first frame, top of the photo): motion on for this one.
        CompositionLocalProvider(LocalReduceMotion provides false) {
            Box(Modifier.fillMaxSize().padding(top = topInset() + Dimens.HeaderTop, bottom = padding.calculateBottomPadding())) {
                ScanLayout(ScanPhase.Reading, photo = receiptStill().asImageBitmap(), onBack = {}, onCapture = {}) {}
            }
        }
    }

    @Test
    fun removedMessage() = shell("removed-message") { padding ->
        Box(Modifier.fillMaxSize()) {
            HomeScreen(HomeUiState(currentUserName = "Christophe", currentUserAvatar = "avatar_christophe"), padding)
            Box(Modifier.align(Alignment.TopCenter)) { ShellNotice("You were removed from Maxi's party", onDone = {}) }
        }
    }

    @Test
    fun leaveConfirmation() = shell("leave-confirm") { padding ->
        Box(Modifier.fillMaxSize()) {
            PartyScreen(party, padding, onBack = {})
            // The platform dialog dims what is behind it; drawn here as a 40% black scrim.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) {
                ConfirmCard(
                    title = "Leave Maxi's party?",
                    body = "Your share goes back to the others. You can rejoin from Home while the party is open.",
                    confirmLabel = "Leave",
                    cancelLabel = "Stay",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
    }

    /** The party header with the total card under it, as it sits below the code and members cards. */
    private fun totalOnly(name: String, state: PartyUiState) = shell(name) { padding ->
        Column(Modifier.fillMaxSize().padding(top = topInset() + Dimens.HeaderTop, bottom = padding.calculateBottomPadding())) {
            PartyHeader("Split Party at Tony's", onBack = {})
            Spacer(Modifier.height(Dimens.ContentSpacing))
            TotalArea(state, TotalActions.None)
        }
    }

    private fun overlay(name: String, step: TotalStep) = shell(name) { padding ->
        TotalEntryOverlay(
            step = step,
            state = party.copy(isHost = true),
            contentPadding = padding,
            onStep = {},
            onConfirmBill = {},
            onConfirmTip = {},
            scanScreen = {}
        )
    }

    private fun shell(name: String, content: @Composable (PaddingValues) -> Unit) =
        paparazzi.screen(name) { ShellFrame(current = Tab.Home, onSelect = {}, content = content) }
}

/** System theme dark: the app is light-only, so this render must match total-tip-chosen. */
class TotalDarkScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = SnapshotDevice.copy(nightMode = NightMode.NIGHT),
        useDeviceResolution = true,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.1
    )

    @Test
    fun tipChosenInDarkMode() = paparazzi.screen("total-tip-chosen-dark") {
        ShellFrame(current = Tab.Home, onSelect = {}) { padding ->
            Column(Modifier.fillMaxSize().padding(top = topInset() + Dimens.HeaderTop, bottom = padding.calculateBottomPadding())) {
                PartyHeader("Split Party at Tony's", onBack = {})
                Spacer(Modifier.height(Dimens.ContentSpacing))
                TotalArea(
                    PartyUiState(
                        phase = PartyPhase.Open,
                        isHost = true,
                        total = TotalUi(10_000, TipChoice.Percent(18), 1_800, 11_800, 3, 3_934)
                    ),
                    TotalActions.None
                )
            }
        }
    }
}

/** A stand-in receipt photo (no real receipt image is ever committed). */
private fun receiptStill(): Bitmap {
    val bitmap = Bitmap.createBitmap(900, 1400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFF3A3A3C.toInt())
    val paper = Paint().apply { color = 0xFFFAFAF7.toInt() }
    canvas.drawRect(150f, 80f, 750f, 1320f, paper)
    val ink = Paint().apply {
        color = 0xFF222222.toInt()
        textSize = 38f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    listOf(
        "    TONY'S",
        " Greenwich, CT",
        "",
        "2 Margherita  38.00",
        "1 Caesar      12.50",
        "",
        "Subtotal      50.50",
        "Tax            3.21",
        "Total         53.71"
    ).forEachIndexed { i, line -> canvas.drawText(line, 190f, 200f + i * 70f, ink) }
    return bitmap
}
