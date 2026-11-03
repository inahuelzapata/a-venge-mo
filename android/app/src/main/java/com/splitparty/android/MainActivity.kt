package com.splitparty.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaceholderScreen(
                        target = FirestoreTarget.fromBuildConfig(),
                        hasFirebaseConfigFile = BuildConfig.HAS_GOOGLE_SERVICES_JSON,
                    )
                }
            }
        }
    }
}

private val warningColor = Color(0xFFE65100)

/**
 * Temporary first screen. The app-shell Work Order replaces it with the real
 * navigation; it exists so the project builds and runs on a device, and so a
 * missing local setup step is visible instead of silent.
 */
@Composable
fun PlaceholderScreen(target: FirestoreTarget, hasFirebaseConfigFile: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", fontSize = 64.sp)
        Text("Split Party", style = MaterialTheme.typography.headlineLarge)
        Text("Android placeholder", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.summary)
                if (target is FirestoreTarget.NotConfigured) {
                    Text(target.reason, color = warningColor, style = MaterialTheme.typography.bodySmall)
                }
                if (!hasFirebaseConfigFile) {
                    Text("google-services.json missing (see android/README.md)", color = warningColor)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    MaterialTheme {
        PlaceholderScreen(FirestoreTarget.Emulator("demo-split-party", "192.168.1.20:8080"), hasFirebaseConfigFile = false)
    }
}
