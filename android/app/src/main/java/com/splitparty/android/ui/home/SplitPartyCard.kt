package com.splitparty.android.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.splitparty.android.R
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type

/**
 * The Home feed's entry to Split Party (PoC-7): the bundled "Split the check
 * with friends" banner above a black "Start a party" pill, on a white feed card.
 * Modelled on the Kalshi ad card of docs/design/home-feed-reference-1.jpg minus
 * its logo/name/"Sponsored" header. Renders from bundled artwork only (no
 * Firestore read), so it never waits on or reacts to data.
 *
 * The banner art is brand/banners/split-the-check-with-friends.png, copied into
 * generated resources at build time (see app/build.gradle.kts).
 */
@Composable
fun SplitPartyCard(onStartParty: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    val banner = painterResource(R.drawable.split_party_banner)
    val bannerCopy = stringResource(R.string.split_party_banner_description)
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.BannerCardInset)
    ) {
        // The source's own aspect ratio, so the art is scaled, never cropped: its
        // copy stays whole at every width.
        Image(
            painter = banner,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(banner.intrinsicSize.width / banner.intrinsicSize.height)
                .clip(RoundedCornerShape(Dimens.BannerImageRadius))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onStartParty)
                // TalkBack reads the copy baked into the art, never "unlabelled image".
                .semantics { contentDescription = bannerCopy }
                .testTag(SplitPartyCardTestTags.IMAGE)
        )
        Spacer(Modifier.height(Dimens.BannerButtonGap))
        PillButton(
            text = stringResource(R.string.split_party_start),
            onClick = onStartParty,
            enabled = !busy,
            busy = busy,
            height = Dimens.BannerButtonHeight,
            style = Type.ButtonCompact,
            modifier = Modifier
                .padding(horizontal = Dimens.BannerButtonSideMargin - Dimens.BannerCardInset)
                .testTag(SplitPartyCardTestTags.BUTTON)
        )
        Spacer(Modifier.height(Dimens.BannerButtonBottomMargin - Dimens.BannerCardInset))
    }
}

object SplitPartyCardTestTags {
    const val IMAGE = "split_party_card_image"
    const val BUTTON = "split_party_card_button"
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7, widthDp = 402)
@Composable
private fun SplitPartyCardPreview() {
    SplitPartyTheme { SplitPartyCard(onStartParty = {}) }
}
