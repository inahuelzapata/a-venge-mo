package com.splitparty.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitparty.android.R
import com.splitparty.android.domain.model.DemoMerchant
import com.splitparty.android.domain.model.User
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.PulsingDot
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type

/**
 * A friend's open party (PoC-8): host avatar with a pulsing live dot, "Maxi
 * started a Split Party at Tony's", and one black "Join" capsule. TalkBack
 * reads it as a single button: "Maxi started a Split Party at Tony's, Join".
 * Provisional values (docs/design/visual-conventions.md) until iOS lands.
 */
@Composable
fun JoinBanner(host: User?, hostId: String, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    val hostName = host?.displayName ?: hostId.replaceFirstChar { it.uppercase() }
    val title = stringResource(R.string.join_banner_title, hostName, DemoMerchant.NAME)
    val joinLabel = stringResource(R.string.join)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .testTag(PartyBannerTestTags.JOIN)
            .clickable(interaction, indication = null, onClick = onJoin)
            .clearAndSetSemantics {
                contentDescription = "$title, $joinLabel"
                role = Role.Button
                onClick(label = joinLabel) {
                    onJoin()
                    true
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(host?.avatar, hostName, size = 48.dp)
            PulsingDot(Modifier.align(Alignment.TopEnd), size = 10.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.Amount, color = Brand.Ink)
            Text(stringResource(R.string.join_banner_subtitle), style = Type.Body, color = Brand.InkSecondary)
        }
        Spacer(Modifier.width(12.dp))
        SmallPill(joinLabel, interaction)
    }
}

/** The 36-tall black capsule inside a banner; pressing the banner dims it, as the pill buttons do. */
@Composable
private fun SmallPill(text: String, interaction: MutableInteractionSource, modifier: Modifier = Modifier) {
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(36.dp)
            .alpha(if (pressed) 0.8f else 1f)
            .background(Color.Black, CircleShape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Type.Amount, color = Color.White)
    }
}

/**
 * Shown instead of the merchant card or a Join banner while the user is in an
 * open party, so leaving the party screen never strands them.
 */
@Composable
fun BackToPartyCard(joinCode: List<String>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(16.dp)
            .testTag(PartyBannerTestTags.BACK_TO_PARTY),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot()
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.back_to_party_title, DemoMerchant.NAME),
                style = Type.Amount,
                color = Brand.Ink,
                modifier = Modifier.weight(1f)
            )
            // Decorative: the party screen reads the code out with emoji names.
            Text(joinCode.joinToString(" "), style = Type.Amount, modifier = Modifier.clearAndSetSemantics { })
        }
        PillButton(stringResource(R.string.back_to_party_action), onClick = onOpen)
    }
}

object PartyBannerTestTags {
    const val JOIN = "join_banner"
    const val BACK_TO_PARTY = "back_to_party"
    const val START_ERROR = "start_party_error"
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7, widthDp = 402)
@Composable
private fun PartyBannersPreview() {
    SplitPartyTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            JoinBanner(User("maxi", "maxi", "Maxi", "avatar_maxi", 0), "maxi", onJoin = {})
            BackToPartyCard(listOf("🍕", "🍺", "🎉"), onOpen = {})
        }
    }
}
