package com.splitparty.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.domain.live.OFFLINE_MESSAGE
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.ui.home.HomeScreen
import com.splitparty.android.ui.home.HomeUiState
import com.splitparty.android.ui.login.LoginScreen
import com.splitparty.android.ui.login.LoginUiState
import com.splitparty.android.ui.login.LoginViewModel
import com.splitparty.android.ui.me.MeScreen
import com.splitparty.android.ui.me.MeUiState
import com.splitparty.android.ui.pay.PayRequestScreen
import com.splitparty.android.ui.shell.ShellFrame
import com.splitparty.android.ui.shell.Tab
import com.splitparty.android.ui.theme.LocalMinimumInsets
import com.splitparty.android.ui.theme.MinimumInsets
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test

/**
 * One render per iOS reference capture (docs/design/ios-captures/), same state
 * and same data as the iOS Debug fixture, at the same 402 x 874 geometry. These
 * are the Android side of docs/design/comparisons/ (copied there by
 * docs/design/capture/compare.sh). The status bar is OS-drawn, so it is not part
 * of the render: the page shows through, as it does behind the real status bar.
 */
class ParityScreenshotTest {
    @get:Rule
    val paparazzi = snapshotRule(fullResolution = true)

    private val target = "Emulator 10.0.2.2:8080 (project demo-split-party)"

    @Test
    fun loginEmpty() = login("login-empty", LoginUiState(targetSummary = target))

    @Test
    fun loginFilled() = login("login-filled", LoginUiState(username = "maxi", targetSummary = target))

    @Test
    fun loginError() = login(
        "login-error",
        LoginUiState(username = "thor", error = LoginViewModel.noAccount("thor"), targetSummary = target)
    )

    @Test
    fun loginNotConfigured() = login(
        "login-not-configured",
        LoginUiState(
            targetSummary = "Firestore target not configured",
            setupProblems = persistentListOf(
                "FIREBASE_TARGET is not set. Copy android/firestore.properties.example to firestore.properties and rebuild."
            )
        )
    )

    @Test
    fun homeFeed() = shell("home-feed", Tab.Home) { HomeScreen(home(Fixture.feed), it) }

    @Test
    fun homeNewRowHighlight() = shell("home-new-row-highlight", Tab.Home) {
        HomeScreen(home(listOf(Fixture.incoming) + Fixture.feed).copy(highlighted = persistentSetOf(Fixture.incoming.id)), it)
    }

    @Test
    fun homeEmpty() = shell("home-empty", Tab.Home) { HomeScreen(home(emptyList()), it) }

    /** Users can't load either, so the header avatar is the grey fallback, as on iOS. */
    @Test
    fun homeError() = shell("home-error", Tab.Home) {
        HomeScreen(HomeUiState(currentUserName = "Maxi", feed = LiveState.Unreachable(OFFLINE_MESSAGE)), it)
    }

    @Test
    fun me() = shell("me", Tab.Me) {
        MeScreen(
            MeUiState(
                userId = "maxi",
                displayName = "Maxi",
                avatar = "avatar_maxi",
                profile = LiveState.Ready(Fixture.maxi),
                transactions = LiveState.Ready(Fixture.feed.filter { "maxi" in listOf(it.payerId, it.payeeId) }.toImmutableList()),
                users = Fixture.users
            ),
            contentPadding = it,
            onAction = {}
        )
    }

    @Test
    fun payRequest() = shell("pay-request", Tab.Pay) { PayRequestScreen(it) }

    private fun home(rows: List<Transaction>) = HomeUiState(
        currentUserName = "Maxi",
        currentUserAvatar = "avatar_maxi",
        feed = LiveState.Ready(rows.toImmutableList()),
        users = Fixture.users
    )

    private fun shell(name: String, tab: Tab, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) =
        paparazzi.screen(name) { ShellFrame(current = tab, onSelect = {}, content = content) }

    /**
     * The login field focuses itself, so the iOS captures show the keyboard.
     * The OS draws the keyboard, so here a flat stand-in of the iOS keyboard's
     * height (329) takes its place and the screen lays out above it, as
     * adjustResize does on a phone.
     */
    private fun login(name: String, state: LoginUiState) = paparazzi.screen(name) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalMinimumInsets provides MinimumInsets(bottom = 0.dp)) {
                    LoginScreen(state, onAction = {})
                }
            }
            Box(Modifier.fillMaxWidth().height(KEYBOARD_HEIGHT.dp).background(KeyboardStandIn))
        }
    }

    private companion object {
        const val KEYBOARD_HEIGHT = 329
        val KeyboardStandIn = Color(0xFFD1D3D9)
    }
}

/** The iOS Debug fixture (ios/SplitParty/Data/FixtureDataSource.swift): same users, rows and ages. */
private object Fixture {
    private const val HOUR = 3_600_000L
    private val now = System.currentTimeMillis()

    val maxi = User("maxi", "maxi", "Maxi", "avatar_maxi", 25_000)
    private val annie = User("annie", "annie", "Annie", "avatar_annie", 18_050)
    private val christophe = User("christophe", "christophe", "Christophe", "avatar_christophe", 32_075)
    val users = persistentMapOf(maxi.id to maxi, annie.id to annie, christophe.id to christophe)

    private fun tx(id: String, payer: String, payee: String, cents: Long, memo: String, ageHours: Long, likes: Long, comments: Long) =
        Transaction(id, payer, payee, cents, memo, null, null, now - ageHours * HOUR, likes, comments)

    val feed = listOf(
        tx("seed_tx_06", "maxi", "annie", 900, "🍦 ice cream run", 3, 1, 1),
        tx("seed_tx_05", "annie", "christophe", 3000, "Uber home 🚕", 12, 0, 0),
        tx("seed_tx_04", "christophe", "maxi", 2575, "Tony's Split Party", 48, 2, 2),
        tx("seed_tx_03", "maxi", "christophe", 1200, "Coffee ☕", 72, 1, 0),
        tx("seed_tx_02", "christophe", "annie", 4200, "Concert tickets 🎸", 96, 3, 1),
        tx("seed_tx_01", "annie", "maxi", 1850, "🌮 Taco Tuesday", 120, 2, 0)
    )
    val incoming = Transaction("fixture_incoming", "annie", "maxi", 3115, "Tony's Split Party", "Tony's", null, now, 0, 0)
}
