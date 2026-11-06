package com.splitparty.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.splitparty.android.R

/**
 * Inter (SIL OFL 1.1, licence in assets/licenses/Inter-OFL.txt) stands in for
 * Apple's SF Pro, which can't be bundled. Every text role uses it.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** SF Pro's default line height is 1.19 × the font size. */
private const val LINE_HEIGHT = 1.19f

/**
 * Inter sets a little wider than SF Pro. These trackings make lines as long
 * as in the iOS captures (checked in docs/design/comparisons/).
 */
private val TRACKING_SMALL = (-0.027).em
private val TRACKING_TEXT = (-0.035).em
private val TRACKING_DISPLAY = (-0.03).em
private val TRACKING_DIGITS = (-0.008).em

private fun role(size: Int, weight: FontWeight, tracking: TextUnit = if (size <= 18) TRACKING_SMALL else TRACKING_TEXT) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * LINE_HEIGHT).sp,
    letterSpacing = tracking,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

/** Text roles of docs/design/visual-conventions.md, in iOS sizes and weights. */
object Type {
    val Wordmark = role(40, FontWeight.Bold, TRACKING_DISPLAY)
    val Balance = role(36, FontWeight.Bold, TRACKING_DIGITS)
    val ProfileName = role(26, FontWeight.Bold, TRACKING_TEXT)
    val PlaceholderTitle = role(24, FontWeight.Bold, TRACKING_DISPLAY)
    val ScreenTitle = role(20, FontWeight.Bold)
    val SectionTitle = role(20, FontWeight.SemiBold)
    val RowPrimary = role(20, FontWeight.Normal)
    val Field = role(18, FontWeight.Normal)
    val Button = role(18, FontWeight.SemiBold)

    /** Compact card actions ("Start a party" on the Split the check card). */
    val ButtonCompact = role(15, FontWeight.SemiBold)
    val Amount = role(17, FontWeight.SemiBold)
    val Subtitle = role(17, FontWeight.Normal)
    val RowSecondary = role(15, FontWeight.Normal)
    val ChipCount = role(15, FontWeight.Medium)
    val Body = role(15, FontWeight.Normal)
    val Footer = role(13, FontWeight.Normal)

    /** The white "$" in the Pay/Request circle (iOS: the heavy dollarsign symbol). */
    val DollarGlyph = role(26, FontWeight.Bold, 0.em)
    val TabLabel = role(11, FontWeight.Normal, 0.em)
    val TabLabelSelected = role(11, FontWeight.SemiBold, 0.em)
}
