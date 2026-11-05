package com.splitparty.android.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitparty.android.BuildConfig
import com.splitparty.android.MainActivity
import com.splitparty.android.ui.login.LoginTestTags
import com.splitparty.android.ui.me.MeTestTags
import com.splitparty.android.ui.party.PartyTestTags
import com.splitparty.android.ui.shell.ShellTestTags
import com.splitparty.android.ui.shell.Tab
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Resets the REAL demo: the installed app (real Koin, real Firebase target from
 * firestore.properties) taps Me → Demo reset → Reset and waits for "Demo reset."
 * Every phone on the project goes back to the seeded state.
 *
 * Skipped unless the instrumentation argument `liveDemoReset=true` is passed,
 * so connectedDebugAndroidTest never resets the hosted demo. Run it with
 * android/scripts/demo-reset.sh (am instrument: the app's sign-in survives).
 * Optional argument `liveDemoResetUser` (default maxi) signs in when the app is signed out.
 */
@RunWith(AndroidJUnit4::class)
class LiveDemoResetTest {
    private val args = InstrumentationRegistry.getArguments()

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoResetRestoresTheSeededState() {
        assumeTrue("No liveDemoReset=true argument: this test resets the real demo", args.getString("liveDemoReset") == "true")
        val user = args.getString("liveDemoResetUser") ?: "maxi"
        val meTab = ShellTestTags.tab(Tab.Me)

        compose.waitUntil(TIMEOUT_MS) { compose.has(meTab) || compose.has(LoginTestTags.USERNAME) }
        if (!compose.has(meTab)) {
            compose.onNodeWithTag(LoginTestTags.USERNAME).performTextInput(user)
            compose.onNodeWithTag(LoginTestTags.SUBMIT).performClick()
            compose.waitUntil(TIMEOUT_MS) { compose.has(meTab) || compose.has(LoginTestTags.ERROR) }
            if (!compose.has(meTab)) fail("Sign-in as '$user' failed: ${compose.textOf(LoginTestTags.ERROR)}")
        }

        compose.onNodeWithTag(meTab).performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.has(MeTestTags.LIST) }
        compose.onNodeWithTag(MeTestTags.LIST).performScrollToNode(hasTestTag(MeTestTags.DEMO_RESET))
        compose.onNodeWithTag(MeTestTags.DEMO_RESET).performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.has(PartyTestTags.DIALOG_CONFIRM) }
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()

        // The reset re-seeds "Your payments" above this section, so the list jumps
        // back up and the (lazy) section is no longer composed: scroll to the outcome.
        val outcome = hasTestTag(MeTestTags.DEMO_RESET_DONE) or hasTestTag(MeTestTags.DEMO_RESET_ERROR)
        compose.waitUntil(RESET_TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(MeTestTags.LIST).performScrollToNode(outcome) }.isSuccess
        }
        if (!compose.has(MeTestTags.DEMO_RESET_DONE)) {
            fail("Demo reset failed (RESET_DEMO_URL='${BuildConfig.RESET_DEMO_URL}'): ${compose.textOf(MeTestTags.DEMO_RESET_ERROR)}")
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L

        // The endpoint's maxDuration is 30 s; a cold start adds a few.
        const val RESET_TIMEOUT_MS = 45_000L
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.has(tag: String) =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

private fun androidx.compose.ui.test.junit4.ComposeTestRule.textOf(tag: String): String =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
        ?.joinToString { it.text } ?: "(no message shown)"
