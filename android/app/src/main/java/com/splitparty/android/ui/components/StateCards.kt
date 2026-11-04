package com.splitparty.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitparty.android.ui.theme.Brand

@Composable
fun MessageCard(emoji: String, title: String, message: String, modifier: Modifier = Modifier, titleColor: Color = Brand.Ink) {
    Column(
        modifier = modifier.fillMaxWidth().background(Brand.Card, RoundedCornerShape(28.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 36.sp)
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            textAlign = TextAlign.Center
        )
        Text(message, color = Brand.InkSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Brand.Blue, strokeWidth = 3.dp)
    }
}

@Composable
fun UnreachableCard(message: String, modifier: Modifier = Modifier) =
    MessageCard("📡", "Can't load payments", message, titleColor = Brand.Error, modifier = modifier)
