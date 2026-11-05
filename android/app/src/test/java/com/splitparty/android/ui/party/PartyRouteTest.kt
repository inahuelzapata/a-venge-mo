package com.splitparty.android.ui.party

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.party
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.ui.scan.ReceiptRecognizer
import com.splitparty.android.ui.scan.ScanOutcome
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The party route with the real ViewModel over fake data: keypad, custom tip and system back. */
// A phone-sized screen (Pixel 5, as the screenshots): the keypad needs real height.
@Config(qualifiers = "w393dp-h851dp")
@RunWith(AndroidJUnit4::class)
class PartyRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val parties = FakePartyRepository()
    private val users = FakeSplitPartyRepository().apply { users.value = server(TestData.everyone.associateBy { it.id }) }

    /** Answers every permission request with "deny", as when camera access is off in settings. */
    private val denyingRegistry = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                dispatchResult(requestCode, false)
            }
        }
    }

    @Before
    fun startDi() {
        startKoin {
            modules(
                module {
                    single<ReceiptRecognizer> {
                        object : ReceiptRecognizer {
                            override suspend fun warmUp() = Unit

                            override suspend fun recognize(photo: Bitmap) = ScanOutcome.NoTotal
                        }
                    }
                }
            )
        }
    }

    @After
    fun stopDi() = stopKoin()

    private fun setCamera(present: Boolean) =
        shadowOf(compose.activity.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, present)

    private fun launch(userId: String = "maxi", billCents: Long? = null, tip: Tip? = null) {
        parties.partyFlow("p1").value = server(
            party(host = "maxi", members = listOf("maxi", "annie", "christophe"), createdAt = NOW, billCents = billCents, tip = tip)
        )
        val vm = PartyViewModel("p1", userId, parties, ObserveUsersUseCase(users), FakeConnectivityObserver(), clock = { NOW + 1_000 })
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true, LocalActivityResultRegistryOwner provides denyingRegistry) {
                SplitPartyTheme { PartyRoute("p1", userId, PaddingValues(), onHome = {}, viewModel = vm) }
            }
        }
    }

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun type(text: String) = text.forEach { compose.onNodeWithTag(EntryTestTags.key(it.toString())).performClick() }

    private fun confirm() = compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()

    private fun openKeypad() = compose.onNodeWithTag(PartyTestTags.ENTER).performScrollTo().performClick()

    @Test
    fun typing48_50AndConfirmingWrites4850AndShowsTheShare() {
        launch()
        openKeypad()
        type("48.50")
        compose.onNodeWithContentDescription("48 dollars and 50 cents").assertIsDisplayed()
        confirm()
        compose.waitForIdle()
        assertEquals(listOf("bill:4850"), parties.writes)
        // Back on the party screen: 48.50 / 3 = 16.16 (host carries the 2 leftover cents).
        compose.onNodeWithContentDescription("Your share, 16 dollars and 18 cents").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun outOfRangeOrThreeDecimalsAreBlockedInlineAndNothingIsWritten() {
        launch()
        openKeypad()
        for ((input, message) in listOf(
            "0" to "The amount must be at least $0.01.",
            "10000.00" to "The amount can't be more than $9,999.99.",
            "12.345" to "Use at most two decimals, like 48.50.",
            "" to "Type an amount first."
        )) {
            type(input)
            confirm()
            compose.onNodeWithText(message).assertIsDisplayed()
            repeat(input.length) { compose.onNodeWithTag(EntryTestTags.key(KEY_DELETE)).performClick() }
        }
        assertTrue(parties.writes.isEmpty())
    }

    @Test
    fun theRangeBoundsAreAccepted() {
        launch()
        openKeypad()
        type("0.01")
        confirm()
        compose.waitForIdle()
        openKeypad()
        // Pre-filled with the current bill; clear it first.
        repeat(4) { compose.onNodeWithTag(EntryTestTags.key(KEY_DELETE)).performClick() }
        type("9999.99")
        confirm()
        compose.waitForIdle()
        assertEquals(listOf("bill:1", "bill:999999"), parties.writes)
    }

    @Test
    fun systemBackFromTheKeypadReturnsToThePartyWithNothingWritten() {
        launch()
        openKeypad()
        type("48.50")
        pressBack()
        compose.onNodeWithText("Add the total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(EntryTestTags.CONFIRM).assertDoesNotExist()
        assertTrue(parties.writes.isEmpty())
    }

    @Test
    fun customSevenDollarTipIsWrittenAsAnAmount() {
        launch(billCents = 10_000, tip = Tip.Percent(18))
        compose.onNodeWithTag(PartyTestTags.tipChip("Custom")).performScrollTo().performClick()
        type("7")
        confirm()
        compose.waitForIdle()
        assertEquals(listOf("tip:${Tip.Custom(700)}"), parties.writes)
        compose.onNodeWithContentDescription("Your share, 35 dollars and 68 cents").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cameraDeniedExplainsAndLandsOnTheKeypadWhere48_50Writes4850() {
        setCamera(true)
        launch()
        compose.onNodeWithTag(PartyTestTags.SCAN).performScrollTo().performClick()
        compose.onNodeWithText("Camera access is off, so the receipt can't be scanned. Type the total in instead.").assertIsDisplayed()
        type("48.50")
        confirm()
        compose.waitForIdle()
        assertEquals(listOf("bill:4850"), parties.writes)
    }

    @Test
    fun noCameraExplainsAndLandsOnTheKeypad() {
        setCamera(false)
        launch()
        compose.onNodeWithTag(PartyTestTags.SCAN).performScrollTo().performClick()
        compose.onNodeWithTag(EntryTestTags.NOTICE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This phone has no camera available, so the receipt can't be scanned. Type the total in instead.")
            .assertIsDisplayed()
        assertTrue(parties.writes.isEmpty())
    }

    @Test
    fun membersGetNoEntryControls() {
        launch(userId = "annie")
        compose.onNodeWithText("Waiting for the total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PartyTestTags.ENTER).assertDoesNotExist()
        compose.onNodeWithTag(PartyTestTags.SCAN).assertDoesNotExist()
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
