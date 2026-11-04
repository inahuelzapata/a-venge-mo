package com.splitparty.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.splitparty.android.R
import com.splitparty.android.ui.theme.Brand

/**
 * The Aven-mo mark (vector drawable generated from brand/aven-mo-mark.svg, so it
 * stays sharp at every density); its sizes are in Dimens. Decorative: no content
 * description; a control that shows only the mark carries its own. Fixed colours
 * only, so it looks the same when the phone is in dark theme.
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
