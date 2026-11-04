package com.splitparty.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitparty.android.R
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type

/**
 * Empty and error states, as in docs/design/ios-captures/home-empty.png and
 * home-error.png: a white card with a blue icon, a title and a grey message.
 */
@Composable
fun MessageCard(
    @DrawableRes icon: Int,
    iconWidth: Dp,
    iconHeight: Dp,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconLine: Dp = ICON_LINE,
    showsReconnecting: Boolean = false
) {
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.StatusCardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The iOS symbol line (36 for most, taller for wifi.exclamationmark); the glyph may overhang it.
        Box(Modifier.size(iconWidth, iconLine), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Brand.Blue,
                modifier = Modifier.requiredSize(iconWidth, iconHeight)
            )
        }
        Text(title, style = Type.SectionTitle, color = Brand.Ink, textAlign = TextAlign.Center)
        Text(message, style = Type.Body, color = Brand.InkSecondary, textAlign = TextAlign.Center)
        if (showsReconnecting) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivitySpinner()
                Spacer(Modifier.size(8.dp))
                Text("Reconnecting…", style = Type.Body, color = Brand.InkSecondary)
            }
        }
    }
}

private val ICON_LINE = 36.dp

/** "No payments yet": the tray symbol. */
@Composable
fun EmptyCard(title: String, message: String, modifier: Modifier = Modifier) =
    MessageCard(R.drawable.ic_tray, iconWidth = 52.dp, iconHeight = 43.dp, title = title, message = message, modifier = modifier)

/** First load: the iOS spinner in a 200-tall area. */
@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        ActivitySpinner()
    }
}

@Composable
fun UnreachableCard(message: String, modifier: Modifier = Modifier) = MessageCard(
    icon = R.drawable.ic_wifi_exclamationmark,
    iconWidth = 45.dp,
    iconHeight = 40.dp,
    iconLine = 42.5.dp,
    title = "Can't load payments",
    message = message,
    showsReconnecting = true,
    modifier = modifier
)
