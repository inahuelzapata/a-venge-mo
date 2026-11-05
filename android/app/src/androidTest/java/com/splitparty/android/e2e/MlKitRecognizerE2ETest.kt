package com.splitparty.android.e2e

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.data.MlKitReceiptRecognizer
import com.splitparty.android.ui.scan.ScanOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real, bundled ML Kit model on the device (no network needed): it reads a
 * receipt rendered like a printed one and picks the Total line, not the
 * subtotal or tax, within the scan's time budget. No receipt image is
 * committed; the receipt is drawn here.
 */
@RunWith(AndroidJUnit4::class)
class MlKitRecognizerE2ETest {
    private val recognizer = MlKitReceiptRecognizer()

    @Test
    fun readsTheTotalLineNotSubtotalOrTaxWithinTheBudget() = runBlocking {
        // The app warms the model while the host aims the camera.
        recognizer.warmUp()
        val receipt = receipt(
            "TONY'S" to null,
            "Greenwich Ave, CT" to null,
            "2 Margherita" to "38.00",
            "1 Caesar" to "12.50",
            "Subtotal" to "50.50",
            "Tax" to "3.21",
            "Total" to "53.71",
            "Thank you!" to null
        )
        val started = SystemClock.elapsedRealtime()
        val outcome = recognizer.recognize(receipt)
        val elapsed = SystemClock.elapsedRealtime() - started

        assertEquals(5_371L, (outcome as? ScanOutcome.Found)?.cents)
        assertTrue("Recognition took $elapsed ms; the scan budget is 2500 ms", elapsed < RECOGNITION_BUDGET_MS)
    }

    @Test
    fun aBlankPageFindsNoTotal() = runBlocking {
        val blank = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        assertEquals(ScanOutcome.NoTotal, recognizer.recognize(blank))
    }

    @Test
    fun aReceiptWithoutATotalLineFindsNoTotal() = runBlocking {
        val noTotal = receipt("TONY'S" to null, "2 Margherita" to "38.00", "1 Caesar" to "12.50")
        assertEquals(ScanOutcome.NoTotal, recognizer.recognize(noTotal))
    }

    /** Thermal-receipt style: black monospace on white, labels left, amounts right. */
    private fun receipt(vararg rows: Pair<String, String?>): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply {
            color = Color.BLACK
            textSize = TEXT_PX
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        rows.forEachIndexed { i, (label, amount) ->
            val y = TOP_PX + i * ROW_PX
            canvas.drawText(label, LEFT_PX, y, ink)
            if (amount != null) canvas.drawText(amount, WIDTH - LEFT_PX - ink.measureText(amount), y, ink)
        }
        return bitmap
    }

    private companion object {
        const val RECOGNITION_BUDGET_MS = 2_500L
        const val WIDTH = 900
        const val HEIGHT = 1_400
        const val TEXT_PX = 44f
        const val LEFT_PX = 60f
        const val TOP_PX = 140f
        const val ROW_PX = 90f
    }
}
