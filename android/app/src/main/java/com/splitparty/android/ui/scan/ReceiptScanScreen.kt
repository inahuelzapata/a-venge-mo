package com.splitparty.android.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.splitparty.android.R
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.party.EntryNotice
import com.splitparty.android.ui.party.PartyHeader
import com.splitparty.android.ui.party.TotalStep
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.Type
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject

/** What recognition made of the photo. */
sealed interface ScanOutcome {
    data class Found(val cents: Long, val line: String) : ScanOutcome

    /** Text was read but no total line (or a blank page). */
    data object NoTotal : ScanOutcome

    /** The photo could not be processed at all. */
    data object Unreadable : ScanOutcome
}

/** On-device recognition of a receipt photo held in memory. */
interface ReceiptRecognizer {
    suspend fun warmUp()

    suspend fun recognize(photo: Bitmap): ScanOutcome
}

enum class ScanPhase { Aiming, Reading }

/** Recognition gets this long; with capture that keeps the whole scan under 3 s. */
private const val RECOGNITION_BUDGET_MS = 2_500L

/** Longest side the photo is scaled to before recognition: plenty for receipt text, and fast. */
private const val MAX_PHOTO_SIDE = 1_600

/**
 * "Scan receipt": asks for the camera only now, then shows the live preview;
 * on capture the photo freezes under a sweeping laser line while ML Kit reads
 * it on the phone. The result goes to [onResult] as the next step: the
 * confirmation (a total was found) or the keypad with the reason. The photo
 * lives only in memory and is dropped when this screen leaves.
 */
@Composable
fun ReceiptScanRoute(onBack: () -> Unit, onResult: (TotalStep) -> Unit, recognizer: ReceiptRecognizer = koinInject()) {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onResult(TotalStep.EnterBill(notice = EntryNotice.CameraDenied))
    }
    LaunchedEffect(Unit) {
        when {
            !hasCamera -> currentOnResult(TotalStep.EnterBill(notice = EntryNotice.NoCamera))
            !granted -> launcher.launch(Manifest.permission.CAMERA)
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { recognizer.warmUp() }
    if (!hasCamera || !granted) {
        ScanLayout(ScanPhase.Aiming, photo = null, onBack = onBack, onCapture = {}, captureEnabled = false) {}
        return
    }

    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var phase by remember { mutableStateOf(ScanPhase.Aiming) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    // Privacy: the frozen photo is dropped when the screen leaves, whatever happened.
    DisposableEffect(Unit) { onDispose { photo = null } }

    ScanLayout(
        phase = phase,
        photo = photo,
        onBack = onBack,
        onCapture = {
            phase = ScanPhase.Reading
            scope.launch {
                val bitmap = runCatching { imageCapture.capture(context) }.getOrNull()
                if (bitmap == null) {
                    onResult(TotalStep.EnterBill(notice = EntryNotice.Unreadable))
                    return@launch
                }
                photo = bitmap.asImageBitmap()
                val readingSince = SystemClock.elapsedRealtime()
                val outcome = withTimeoutOrNull(RECOGNITION_BUDGET_MS) { recognizer.recognize(bitmap) } ?: ScanOutcome.Unreadable
                // As on iOS, the laser stays at least 0.7 s, so a fast read still shows it.
                delay(MIN_READING_MS - (SystemClock.elapsedRealtime() - readingSince))
                photo = null
                onResult(
                    when (outcome) {
                        is ScanOutcome.Found -> TotalStep.ConfirmScan(outcome.cents, outcome.line)
                        ScanOutcome.NoTotal -> TotalStep.EnterBill(notice = EntryNotice.NoTotalFound)
                        ScanOutcome.Unreadable -> TotalStep.EnterBill(notice = EntryNotice.Unreadable)
                    }
                )
            }
        }
    ) {
        CameraPreview(imageCapture, onUnavailable = { onResult(TotalStep.EnterBill(notice = EntryNotice.NoCamera)) })
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraPreview(imageCapture: ImageCapture, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // CameraX binding and PreviewView must be touched on the main thread, whatever
    // thread the effect resumes on after awaiting the provider.
    LaunchedEffect(lifecycleOwner) {
        withContext(Dispatchers.Main) { bindCamera(context, lifecycleOwner, previewView, imageCapture) }
            ?.let { provider = it }
            ?: currentOnUnavailable()
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Binds preview and capture to the back camera; null when there is none or binding fails. */
private suspend fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture
): ProcessCameraProvider? {
    val cameras = runCatching { ProcessCameraProvider.awaitInstance(context) }.getOrNull()
        ?.takeIf { it.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }
        ?: return null
    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
    return runCatching {
        cameras.unbindAll()
        cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        cameras
    }.getOrNull()
}

/** One still, into memory only (no file, no MediaStore), upright and scaled down for recognition. */
private suspend fun ImageCapture.capture(context: Context): Bitmap = suspendCancellableCoroutine { cont ->
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.use { it.toUprightBitmap() }
                cont.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        }
    )
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val scale = minOf(1f, MAX_PHOTO_SIDE.toFloat() / maxOf(raw.width, raw.height))
    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        postScale(scale, scale)
    }
    val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (upright !== raw) raw.recycle()
    return upright
}

/**
 * The scan screen's layout, stateless (screenshots render it with a still).
 * Header, the camera or frozen photo in a rounded frame with the laser sweep
 * while reading, the status line (announced), and the Scan button.
 */
@Composable
fun ScanLayout(
    phase: ScanPhase,
    photo: ImageBitmap?,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
    captureEnabled: Boolean = true,
    camera: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        PartyHeader(stringResource(R.string.total_scan), onBack)
        Spacer(Modifier.height(Dimens.ContentSpacing))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Dimens.HeaderMargin)
                .clip(RoundedCornerShape(Dimens.CardRadius))
                .background(Color.Black)
                .testTag(ScanTestTags.VIEWPORT)
        ) {
            if (photo != null) {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                camera()
            }
            if (phase == ScanPhase.Reading && !LocalReduceMotion.current) LaserSweep(Modifier.fillMaxSize())
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.HeaderMargin, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (phase == ScanPhase.Reading) R.string.scan_reading else R.string.scan_aim),
                style = Type.Body,
                color = Brand.InkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(ScanTestTags.STATUS)
            )
            PillButton(
                stringResource(R.string.scan_capture),
                onClick = onCapture,
                enabled = captureEnabled && phase == ScanPhase.Aiming,
                busy = phase == ScanPhase.Reading,
                modifier = Modifier.testTag(ScanTestTags.CAPTURE)
            )
        }
    }
}

private const val SWEEP_MS = 1_100
private const val GLOW_DP = 18
private const val MIN_READING_MS = 700L

/** A brand-blue line sweeping down and up the photo (1.1 s each way, ease-in-out, as on iOS) in a soft glow. */
@Composable
private fun LaserSweep(modifier: Modifier = Modifier) {
    val y by rememberInfiniteTransition(label = "laser").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(SWEEP_MS, easing = EaseInOut), RepeatMode.Reverse),
        label = "laserY"
    )
    Canvas(modifier.testTag(ScanTestTags.LASER)) {
        val lineY = size.height * y
        val glow = GLOW_DP.dp.toPx()
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Brand.Blue.copy(alpha = 0.35f), Color.Transparent),
                startY = lineY - glow,
                endY = lineY + glow
            ),
            topLeft = Offset(0f, lineY - glow),
            size = Size(size.width, glow * 2)
        )
        drawLine(Brand.Blue, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 3.dp.toPx())
    }
}

object ScanTestTags {
    const val VIEWPORT = "scan_viewport"
    const val STATUS = "scan_status"
    const val CAPTURE = "scan_capture"
    const val LASER = "scan_laser"
}
