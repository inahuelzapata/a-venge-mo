package com.splitparty.android.ui.party

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitparty.android.R
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset

/**
 * Where "Start a party" lands until the party screen exists. This is the ONLY
 * placeholder for it: the real screen replaces this composable at the
 * SplitPartyDestination route in MainShell, without touching the Home card.
 *
 * Header like Home's (44 tall, 16 margins, 8 below the safe area) with a round
 * back button; body laid out like the Pay/Request placeholder.
 */
@Composable
fun SplitPartyPlaceholderScreen(contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset() + Dimens.HeaderTop, bottom = contentPadding.calculateBottomPadding())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.HeaderHeight)
                .padding(horizontal = Dimens.HeaderMargin),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.split_party_title),
                style = Type.ScreenTitle,
                color = Brand.Ink,
                modifier = Modifier.semantics { heading() }
            )
            BackButton(onBack, Modifier.align(Alignment.CenterStart))
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(64.dp).background(Brand.Blue, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_fork_knife),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(stringResource(R.string.split_party_in_progress_title), style = Type.PlaceholderTitle, color = Brand.Ink)
            Text(
                stringResource(R.string.split_party_in_progress),
                style = Type.Subtitle,
                color = Brand.InkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(SplitPartyTestTags.BODY)
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimens.HeaderHeight)
            .background(Brand.Card, CircleShape)
            .border(1.dp, Brand.CapsuleOutline, CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onBack)
            .testTag(SplitPartyTestTags.BACK),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_left),
            contentDescription = stringResource(R.string.back),
            tint = Brand.Ink,
            modifier = Modifier.size(22.dp)
        )
    }
}

object SplitPartyTestTags {
    const val BACK = "split_party_back"
    const val BODY = "split_party_body"
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun SplitPartyPlaceholderScreenPreview() {
    SplitPartyTheme { SplitPartyPlaceholderScreen(PaddingValues(), onBack = {}) }
}
