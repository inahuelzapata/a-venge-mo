package com.splitparty.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitparty.android.FirestoreTarget
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme

private val warningColor = Color(0xFFE65100)

/**
 * Shown instead of login when the local Firestore setup is incomplete, so a
 * missing step is visible instead of a silent failure (android/README.md).
 */
@Composable
fun SetupRequiredScreen(target: FirestoreTarget, problems: List<String>) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Split Party", style = MaterialTheme.typography.headlineLarge)
        Text("Setup required", color = Brand.InkSecondary)
        Card(colors = CardDefaults.cardColors(containerColor = Brand.Card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.summary)
                problems.forEach { Text(it, color = warningColor, fontSize = 14.sp) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupRequiredScreenPreview() {
    SplitPartyTheme {
        SetupRequiredScreen(
            FirestoreTarget.NotConfigured("FIREBASE_TARGET is not set."),
            listOf("FIREBASE_TARGET is not set."),
        )
    }
}
