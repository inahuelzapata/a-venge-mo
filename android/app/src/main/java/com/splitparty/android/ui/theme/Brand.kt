package com.splitparty.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Palette measured from the iOS reference captures (docs/design/ios-captures/),
 * recorded in docs/design/visual-conventions.md. The captures are the source of
 * truth: change them first, then the note, then these values.
 */
object Brand {
    val Blue = Color(0xFF0087E0)
    val Page = Color(0xFFF6F6F7)
    val Card = Color.White
    val Ink = Color.Black
    val InkSecondary = Color(0xFF7F7F7F)
    val Placeholder = Color(0xFFC5C5C7)
    val Caret = Color(0xFF426BF2)
    val Chip = Color(0xFFF1F1F3)
    val Error = Color(0xFFD92D20)
    val Warning = Color(0xFFFF8D28)

    /** Brand blue at 14% over a white card. */
    val Highlight = Color(0xFFD4E6F4)

    /** Black at 30% over the page: the disabled pill button. */
    val ButtonDisabled = Color(0xFFACACAD)
    val CapsuleOutline = Color.Black.copy(alpha = 0.08f)
    val TabBar = Color(0xFFF2F2F4)
    val TabBarOutline = Color.Black.copy(alpha = 0.06f)
    val TabSelected = Color.Black.copy(alpha = 0.06f)
    val Shadow = Color.Black

    /** Navy of the Aven-mo mark artwork (brand/aven-mo-mark.svg). */
    val MarkNavy = Color(0xFF021F40)
}

/** Spacing and sizes in dp, equal to the iOS pt values in the conventions note. */
object Dimens {
    /** iOS safe-area insets on the reference iPhone; Android never lays out tighter than these. */
    val ReferenceTopInset = 62.dp
    val ReferenceBottomInset = 34.dp

    val Gutter = 8.dp
    val HeaderMargin = 16.dp
    val HeaderTop = 8.dp
    val HeaderHeight = 44.dp
    val HeaderAvatar = 44.dp
    val ContentSpacing = 16.dp
    val CardRadius = 28.dp
    val CardPadding = 20.dp
    val CardGap = 12.dp
    val CardInnerSpacing = 14.dp
    val InlineAvatar = 22.dp
    val ChipHeight = 44.dp
    val StatusCardPadding = 28.dp

    val PillHeight = 52.dp
    val FieldHeight = 56.dp

    val TabBarSideMargin = 16.dp
    val TabBarBottomMargin = 4.dp
    val TabBarPadding = 6.dp
    val TabItemHeight = 80.dp
    val TabBarHeight = 92.dp
    val TabIconSlot = 52.dp

    /** Space the floating bar covers above the bottom inset; screens keep their content clear of it. */
    val TabBarInset = TabBarHeight + TabBarBottomMargin

    /** Padding below the last row of a list, above the bar (iOS: 24). */
    val ListBottomPadding = 24.dp

    // Aven-mo mark and the centre Pay/Request button ("Brand mark" in the
    // conventions note). A mark size is its square box, which includes a little
    // room above and below the artwork.
    val LoginMark = 72.dp
    /** Between the login mark and the wordmark. */
    val LoginMarkGap = 20.dp
    val HeaderMark = 24.dp
    /** Between the header mark and the "Home" title. */
    val HeaderMarkSpacing = 6.dp
    val PayButtonDiameter = 60.dp
    val PayButtonMark = 30.dp
}
