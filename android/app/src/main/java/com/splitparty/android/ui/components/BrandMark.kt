package com.splitparty.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitparty.android.R
import com.splitparty.android.ui.theme.Brand

/**
 * Sizes of the Aven-mo mark and the centre Pay/Request button. iOS uses the same
 * numbers in points (Theme.swift); both are listed in docs/design/README.md
 * ("Brand mark"). A mark size is its square box, which includes a little room
 * above and below the artwork.
 */
object BrandMarkSizes {
    val LoginMark = 72.dp
    val HeaderMark = 24.dp
    val HeaderMarkSpacing = 6.dp
    val BottomBarHeight = 68.dp
    val PayButtonDiameter = 60.dp
    val PayButtonMark = 30.dp
    /** How far the button's top edge sits above the bar's top edge. */
    val PayButtonLift = 16.dp
    val PayButtonElevation = 8.dp
}

/**
 * The Aven-mo mark (vector drawable generated from brand/aven-mo-mark.svg, so it
 * stays sharp at every density). Decorative: no content description; a control
 * that shows only the mark carries its own. Fixed colours only, so it looks the
 * same when the phone is in dark theme.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier, color: Color = Brand.MarkNavy) {
    Image(
        painter = painterResource(R.drawable.ic_aven_mo_mark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size)
    )
}
