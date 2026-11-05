package com.splitparty.android.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.splitparty.android.domain.receipt.OcrLine
import com.splitparty.android.domain.receipt.ReceiptTotalParser
import com.splitparty.android.ui.scan.ReceiptRecognizer
import com.splitparty.android.ui.scan.ScanOutcome
import kotlinx.coroutines.tasks.await

/**
 * On-device text recognition (ML Kit, bundled Latin model: no download and no
 * network, so it works in airplane mode). The photo is only read here, in
 * memory; it is never written anywhere.
 */
class MlKitReceiptRecognizer : ReceiptRecognizer {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun warmUp() {
        // The first call loads the model; doing it while the host aims keeps the scan itself fast.
        runCatching {
            client.process(InputImage.fromBitmap(Bitmap.createBitmap(WARM_UP_PX, WARM_UP_PX, Bitmap.Config.ARGB_8888), 0)).await()
        }
    }

    override suspend fun recognize(photo: Bitmap): ScanOutcome = try {
        val text = client.process(InputImage.fromBitmap(photo, 0)).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { box -> OcrLine(line.text, box.left, box.top, box.right, box.bottom) }
        }
        ReceiptTotalParser.find(lines)?.let { ScanOutcome.Found(it.cents, it.line) } ?: ScanOutcome.NoTotal
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // MlKitException or anything the decoder throws: the photo could not be read.
        if (e is kotlinx.coroutines.CancellationException) throw e
        ScanOutcome.Unreadable
    }

    private companion object {
        const val WARM_UP_PX = 32
    }
}
