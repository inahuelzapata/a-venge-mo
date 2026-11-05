package com.splitparty.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitparty.android.R
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.Username
import com.splitparty.android.format.Money
import com.splitparty.android.format.RelativeTime
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.motionSpec
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

/** Fade from the highlight back to white once the 1.5 s hold ends: 1 s ease-out, as on iOS. */
private const val HIGHLIGHT_FADE_MS = 1_000

/** The inline avatar sits 4 below the text's centre line, on the baseline as on iOS. */
private const val INLINE_AVATAR_SP = 22

/**
 * One payment card, as in docs/design/ios-captures/home-feed.png: relative time
 * and amount on top, then "(A) Annie paid (M) Maxi for memo", then the comment
 * and like chips. Names are always display names (never "You"), so every phone
 * renders the identical feed.
 */
@Composable
fun FeedRow(transaction: Transaction, users: Map<String, User>, nowMillis: Long, highlighted: Boolean, modifier: Modifier = Modifier) {
    val payer = users[transaction.payerId]
    val payee = users[transaction.payeeId]
    val payerName = payer?.displayName ?: Username.fallbackName(transaction.payerId)
    val payeeName = payee?.displayName ?: Username.fallbackName(transaction.payeeId)
    val background by animateColorAsState(
        targetValue = if (highlighted) Brand.Highlight else Brand.Card,
        animationSpec = motionSpec(tween(durationMillis = HIGHLIGHT_FADE_MS, easing = EaseOut)),
        label = "rowHighlight"
    )

    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = RelativeTime.format(transaction.createdAtMillis, nowMillis),
                style = Type.RowSecondary,
                color = Brand.InkSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(Money.format(transaction.amountCents), style = Type.Amount, color = Brand.Ink)
        }
        Sentence(transaction.memo, payer, payerName, payee, payeeName)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountChip(R.drawable.ic_bubble_left, 24.dp, transaction.commentsCount, "comments")
            CountChip(R.drawable.ic_heart, 22.dp, transaction.likesCount, "likes")
        }
    }
}

@Composable
private fun Sentence(memo: String, payer: User?, payerName: String, payee: User?, payeeName: String) {
    // The inline slot is in sp (it scales with the font size), so size the avatar to match it.
    val avatarSize = INLINE_AVATAR_SP.sp
    val avatarDp = with(LocalDensity.current) { avatarSize.toDp() }
    val sentence = buildAnnotatedString {
        appendInlineContent("payer")
        append(" $payerName")
        withStyle(SpanStyle(color = Brand.InkSecondary)) { append(" paid ") }
        appendInlineContent("payee")
        append(" $payeeName")
        if (memo.isNotEmpty()) {
            withStyle(SpanStyle(color = Brand.InkSecondary)) { append(" for ") }
            append(memo)
        }
    }
    // Like iOS text layout, never leave a lone word on the last line: when that
    // happens, the previous word moves down with it ("ice | cream run").
    var breakAt by remember(sentence) { mutableStateOf<Int?>(null) }
    val shown = breakAt?.let { sentence.subSequence(0, it) + AnnotatedString("\n") + sentence.subSequence(it + 1, sentence.length) }
        ?: sentence
    val avatars = mapOf(
        "payer" to InlineTextContent(Placeholder(avatarSize, avatarSize, PlaceholderVerticalAlign.TextCenter)) {
            Avatar(payer?.avatar, payerName, size = avatarDp)
        },
        "payee" to InlineTextContent(Placeholder(avatarSize, avatarSize, PlaceholderVerticalAlign.TextCenter)) {
            Avatar(payee?.avatar, payeeName, size = avatarDp)
        }
    )
    // iOS lines are 27.5 apart here: the inline avatars make each line taller than plain text.
    Text(
        shown,
        inlineContent = avatars,
        style = Type.RowPrimary.copy(lineHeight = 27.5.sp),
        color = Brand.Ink,
        onTextLayout = { layout ->
            if (breakAt == null) breakAt = orphanBreak(sentence.text, layout)
        }
    )
}

/** Index of the space to turn into a line break so the last line keeps two words, or null. */
private fun orphanBreak(text: String, layout: TextLayoutResult): Int? {
    val lines = layout.lineCount
    val lastLineStart = if (lines < 2) 0 else layout.getLineStart(lines - 1)
    val lastLineWords = text.substring(lastLineStart).split(' ').count { word -> word.any(Char::isLetter) }
    val space = text.lastIndexOf(' ', lastLineStart - 2)
    return space.takeIf { lines >= 2 && lastLineWords < 2 && it > layout.getLineStart(lines - 2) }
}

/** The round grey comment/like button with its mock count. Not tappable in this slice. */
@Composable
private fun CountChip(@DrawableRes icon: Int, iconSize: Dp, count: Long, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(Dimens.ChipHeight)
            .background(Brand.Chip, CircleShape)
            .padding(start = 16.dp, end = 17.5.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$count $label" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = Type.ChipCount, color = Brand.Ink)
    }
}
