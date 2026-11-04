package com.splitparty.android.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.splitparty.android.FirestoreTarget
import com.splitparty.android.ui.theme.Brand

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ANDROID_17 = 37

/** Android 17+ blocks LAN traffic for apps targeting SDK 37 until the user allows it. */
private fun needsLocalNetworkPermission(context: Context, target: FirestoreTarget): Boolean =
    target is FirestoreTarget.Emulator &&
        Build.VERSION.SDK_INT >= ANDROID_17 &&
        ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once the app may reach the Firestore emulator on the laptop.
 * Without the permission Firestore connections to a LAN address silently time
 * out, so ask before login instead of failing with "can't reach the server".
 * The hosted target goes over the internet and never needs it.
 */
@Composable
fun LocalNetworkGate(target: FirestoreTarget, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var needed by remember { mutableStateOf(needsLocalNetworkPermission(context, target)) }
    var denied by remember { mutableStateOf(false) }
    // The user may grant it from system Settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { needed = needsLocalNetworkPermission(context, target) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        needed = !granted
        denied = !granted
    }

    if (!needed) {
        content()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Brand.Card).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📶", fontSize = 48.sp)
        Text("Allow local network", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
        Text(
            "This build talks to the demo database on your laptop (${target.summary}). " +
                "Android needs your permission to reach devices on this Wi-Fi.",
            color = Brand.InkSecondary,
            textAlign = TextAlign.Center,
        )
        if (denied) {
            Text(
                "Still blocked. If no prompt appears, allow it in Settings → Apps → Split Party → Permissions.",
                color = Brand.Error,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        }
        Button(
            onClick = { launcher.launch(ACCESS_LOCAL_NETWORK) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Ink, contentColor = Color.White),
        ) { Text("Allow", fontSize = 17.sp) }
        if (denied) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open Settings", color = Brand.Blue) }
        }
    }
}
