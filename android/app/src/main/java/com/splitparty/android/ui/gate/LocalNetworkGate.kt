package com.splitparty.android.ui.gate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.splitparty.android.R
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.bottomInset
import com.splitparty.android.ui.theme.topInset

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ANDROID_17 = 37

/** Android 17+ blocks LAN traffic for apps targeting SDK 37 until the user allows it. */
private fun needsLocalNetworkPermission(context: Context, target: FirestoreTarget): Boolean = target is FirestoreTarget.Emulator &&
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

    if (needed) {
        LocalNetworkPermissionScreen(
            targetSummary = target.summary,
            denied = denied,
            onAllow = { launcher.launch(ACCESS_LOCAL_NETWORK) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
        )
    } else {
        content()
    }
}

/** Explains why the permission is needed; after a denial it also points to system Settings. */
@Composable
fun LocalNetworkPermissionScreen(
    targetSummary: String,
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // No iOS counterpart (Android-only permission); built from the same tokens as login.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brand.Page)
            .padding(top = topInset(), bottom = bottomInset())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_wifi_exclamationmark),
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(48.dp)
        )
        Text("Allow local network", style = Type.PlaceholderTitle, color = Brand.Ink)
        Text(
            "This build talks to the demo database on your laptop ($targetSummary). " +
                "Android needs your permission to reach devices on this Wi-Fi.",
            style = Type.Body,
            color = Brand.InkSecondary,
            textAlign = TextAlign.Center
        )
        if (denied) {
            Text(
                "Still blocked. If no prompt appears, allow it in Settings → Apps → Split Party → Permissions.",
                style = Type.Body,
                color = Brand.Error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        PillButton(text = "Allow", onClick = onAllow)
        if (denied) {
            Text(
                "Open Settings",
                style = Type.Subtitle,
                color = Brand.Blue,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .padding(12.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalNetworkPermissionScreenPreview() {
    SplitPartyTheme {
        LocalNetworkPermissionScreen("Emulator 10.0.2.2:8080 (project demo-split-party)", denied = true, onAllow = {}, onOpenSettings = {})
    }
}
