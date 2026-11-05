package com.splitparty.android.e2e

import android.Manifest
import android.content.Context
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitparty.android.ui.party.EntryTestTags
import com.splitparty.android.ui.party.PartyTestTags
import com.splitparty.android.ui.scan.ScanOutcome
import com.splitparty.android.ui.scan.ScanTestTags
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * On the device: "Scan receipt" with the real runtime permission and the
 * emulator's camera (CameraX). Recognition is scripted here; the bundled ML Kit
 * model itself is covered by [MlKitRecognizerE2ETest]. Orchestrator clears app
 * data, so every test starts without the camera permission.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptScanE2ETest {
    private val harness = PartyHarness(userId = "maxi")
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(harness).around(compose)

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    /**
     * "Denied in system settings": the permission is not granted and marked
     * user-fixed, so a request is answered "denied" at once with no dialog.
     * (Unlike a revoke, this does not kill the app process.)
     */
    private fun denyCameraInSettings() {
        instrumentation.uiAutomation
            .executeShellCommand("pm set-permission-flags ${context.packageName} ${Manifest.permission.CAMERA} user-set user-fixed")
            .close()
    }

    private fun grantCamera() = instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)

    private fun openAsHostAndTapScan() {
        harness.publish(harness.party())
        compose.launchShell("maxi")
        compose.tapTag(PartyTestTags.SCAN)
    }

    /** Waits for the live preview, then takes the photo. */
    private fun capture() {
        compose.awaitTag(ScanTestTags.CAPTURE)
        // Give CameraX a moment to bind the emulator camera before the shutter.
        Thread.sleep(CAMERA_SETTLE_MS)
        compose.onNodeWithTag(ScanTestTags.CAPTURE).performClick()
    }

    @Test
    fun cameraDeniedInSettingsExplainsAndTheKeypadStillWrites4850() {
        denyCameraInSettings()
        openAsHostAndTapScan()

        // The real permission request runs and comes back denied, with no camera opened.
        compose.awaitTag(EntryTestTags.NOTICE)
        compose.onNodeWithText("Camera access is off, so the receipt can't be scanned. Type the total in instead.").assertIsDisplayed()
        compose.type("48.50")
        compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()
        compose.awaitDescription("Your share, 16 dollars and 18 cents")
        assertEquals(listOf("bill:4850"), harness.parties.writes)
        assertTrue(harness.recognizer.photos.isEmpty())
    }

    @Test
    fun aFoundTotalIsConfirmedBeforeItIsWrittenAndThePhotoIsNotKept() {
        grantCamera()
        harness.recognizer.outcome = ScanOutcome.Found(5_371, "Total")
        val imagesBefore = imageCount()
        val filesBefore = appFiles()

        openAsHostAndTapScan()
        capture()
        compose.awaitTag(EntryTestTags.EDIT, timeoutMs = SCAN_TIMEOUT_MS)
        compose.onNode(hasContentDescription("53 dollars and 71 cents")).assertIsDisplayed()
        compose.onNodeWithText("From the line \"Total\"").assertIsDisplayed()
        // The photo reached recognition, upright and scaled down, and nothing is written yet.
        assertEquals(1, harness.recognizer.photos.size)
        assertTrue(harness.parties.writes.isEmpty())

        compose.onNodeWithTag(EntryTestTags.CONFIRM).performClick()
        compose.awaitDescription("Total, 53 dollars and 71 cents")
        assertEquals(listOf("bill:5371"), harness.parties.writes)

        // Privacy: no new gallery image and no new file in the app's storage.
        assertEquals(imagesBefore, imageCount())
        assertEquals(emptySet<String>(), appFiles() - filesBefore)
    }

    @Test
    fun systemBackFromTheConfirmationWritesNothing() {
        grantCamera()
        harness.recognizer.outcome = ScanOutcome.Found(5_371, "Total")
        openAsHostAndTapScan()
        capture()
        compose.awaitTag(EntryTestTags.EDIT, timeoutMs = SCAN_TIMEOUT_MS)

        Espresso.pressBack()
        compose.awaitTag(PartyTestTags.SCAN)
        compose.onNodeWithText("Add the total").assertExists()
        assertTrue(harness.parties.writes.isEmpty())
    }

    @Test
    fun systemBackFromTheCameraWritesNothing() {
        grantCamera()
        openAsHostAndTapScan()
        compose.awaitTag(ScanTestTags.CAPTURE)
        Espresso.pressBack()
        compose.awaitTag(PartyTestTags.SCAN)
        assertTrue(harness.recognizer.photos.isEmpty())
        assertTrue(harness.parties.writes.isEmpty())
    }

    @Test
    fun noTotalFoundLandsOnTheKeypadWithTheMessage() {
        grantCamera()
        harness.recognizer.outcome = ScanOutcome.NoTotal
        openAsHostAndTapScan()
        capture()

        compose.awaitTag(EntryTestTags.NOTICE, timeoutMs = SCAN_TIMEOUT_MS)
        compose.onNodeWithText("We couldn't find a total on the receipt. Type it in instead.").assertIsDisplayed()
        compose.onNodeWithTag(EntryTestTags.key("1")).assertIsDisplayed()
        assertTrue(harness.parties.writes.isEmpty())
    }

    /** Images in MediaStore this app can see (any it had saved would be among them). */
    private fun imageCount(): Int =
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, null)
            ?.use { it.count } ?: 0

    private fun appFiles(): Set<String> =
        listOfNotNull(context.filesDir, context.cacheDir, context.getExternalFilesDir(null), context.externalCacheDir)
            .flatMap { root -> root.walkTopDown().filter(File::isFile).map { it.absolutePath }.toList() }
            .toSet()

    private companion object {
        const val CAMERA_SETTLE_MS = 1_500L
        const val SCAN_TIMEOUT_MS = 10_000L
    }
}
