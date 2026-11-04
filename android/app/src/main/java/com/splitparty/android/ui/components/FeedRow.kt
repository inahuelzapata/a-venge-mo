package com.splitparty.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitparty.android.data.Transaction
import com.splitparty.android.data.User
import com.splitparty.android.data.Username
import com.splitparty.android.format.Money
import com.splitparty.android.format.RelativeTime
import com.splitparty.android.ui.theme.Brand
import kotlinx.coroutines.delay

/** Wall-clock time that ticks every 30 s, so "x minutes ago" stays current. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * One payment card, laid out like the friend row in
 * docs/design/home-feed-reference-2.jpg. Names are always display names (never
 * "You") so every phone renders the identical feed.
 */
@Composable
fun FeedRow(
    transaction: Transaction,
    users: Map<String, User>,
    nowMillis: Long,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val payer = users[transaction.payerId]
    val payee = users[transaction.payeeId]
    val payerName = payer?.displayName ?: Username.fallbackName(transaction.payerId)
    val payeeName = payee?.displayName ?: Username.fallbackName(transaction.payeeId)
    // A new row starts tinted and fades back to white once `highlighted` turns false.
    val background by animateColorAsState(
        targetValue = if (highlighted) Brand.Highlight else Brand.Card,
        animationSpec = tween(durationMillis = 1_500),
        label = "rowHighlight",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(RelativeTime.format(transaction.createdAtMillis, nowMillis), color = Brand.InkSecondary, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = Brand.Ink)
        }
        Spacer(Modifier.height(12.dp))

        val avatarSize = 22.sp
        val headline = buildAnnotatedString {
            appendInlineContent("payer", "[$payerName avatar]")
            append(" ")
            withStyle(SpanStyle(color = Brand.Ink)) { append(payerName) }
            withStyle(SpanStyle(color = Brand.InkSecondary)) { append(" paid ") }
            appendInlineContent("payee", "[$payeeName avatar]")
            append(" ")
            withStyle(SpanStyle(color = Brand.Ink)) { append(payeeName) }
        }
        val avatars = mapOf(
            "payer" to InlineTextContent(Placeholder(avatarSize, avatarSize, PlaceholderVerticalAlign.Center)) {
                Avatar(payer?.avatar, payerName, size = 22.dp)
            },
            "payee" to InlineTextContent(Placeholder(avatarSize, avatarSize, PlaceholderVerticalAlign.Center)) {
                Avatar(payee?.avatar, payeeName, size = 22.dp)
            },
        )
        Text(headline, inlineContent = avatars, fontSize = 20.sp, lineHeight = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                append(transaction.memo)
                withStyle(SpanStyle(color = Brand.InkSecondary)) { append("  ·  ") }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Money.format(transaction.amountCents)) }
            },
            color = Brand.Ink,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountChip(Icons.Outlined.ChatBubbleOutline, transaction.commentsCount, "comments")
            CountChip(Icons.Outlined.FavoriteBorder, transaction.likesCount, "likes")
        }
    }
}

@Composable
private fun CountChip(icon: ImageVector, count: Long, label: String) {
    Row(
        modifier = Modifier
            .background(Brand.Chip, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = Brand.Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), color = Brand.Ink, fontSize = 15.sp)
    }
}
