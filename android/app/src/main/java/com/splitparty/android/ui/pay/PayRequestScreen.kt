package com.splitparty.android.ui.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset

/**
 * Placeholder tab (Pay/Request is not functional in the PoC shell), as in
 * docs/design/ios-captures/pay-request.png: centred in the space above the bar.
 */
@Composable
fun PayRequestScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset(), bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // iOS draws the 64 circle symbol on a 76 tall text line.
        Box(Modifier.height(76.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).background(Brand.Blue, CircleShape), contentAlignment = Alignment.Center) {
                Text("$", style = Type.DollarGlyph.copy(fontSize = Type.DollarGlyph.fontSize * 1.4f), color = Color.White)
            }
        }
        Text("Pay or request", style = Type.PlaceholderTitle, color = Brand.Ink)
        Text("Coming soon", style = Type.Subtitle, color = Brand.InkSecondary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun PayRequestScreenPreview() {
    SplitPartyTheme { PayRequestScreen(PaddingValues()) }
}
