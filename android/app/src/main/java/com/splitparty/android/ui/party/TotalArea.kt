package com.splitparty.android.ui.party

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.splitparty.android.R
import com.splitparty.android.domain.split.CustomShareProblem
import com.splitparty.android.format.Money
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.RollingAmount
import com.splitparty.android.ui.components.SoftPillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.Type

/**
 * The total area of the party screen (PoC-10). Members: "Waiting for the
 * total", then read-only total/tip/headcount/share. Host: "Add the total",
 * then the same figures with tip chips and "Change total". [frozen] (expired)
 * shows the last figures with no controls.
 */
@Composable
fun TotalArea(state: PartyUiState, actions: TotalActions, modifier: Modifier = Modifier, frozen: Boolean = false) {
    val total = state.total
    val controls = state.isHost && !frozen
    TotalCard(modifier.testTag(PartyTestTags.TOTAL)) {
        when {
            total != null -> {
                ShareFigures(total)
                if (controls) {
                    TipChips(total.tip, enabled = !state.saving, actions)
                    ChangeTotal(enabled = !state.saving, actions)
                }
            }

            controls -> AddTheTotal(enabled = !state.saving, actions)

            else -> Waiting()
        }
        SharesList(state, editable = state.canEditShares && !frozen, onEdit = actions::editShare)
        // The host's Pay, also shown (disabled) once the party expired; members never see it.
        state.pay?.let { pay ->
            val collect = total?.let { t -> t.shareCents?.let { t.totalCents - it } }
            PayButton(
                pay = if (frozen) PayState.Expired else pay,
                collectCents = collect,
                paying = state.paying,
                onPay = actions::pay,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (controls) state.writeError?.let { WriteErrorLine(it) }
    }
}

/**
 * Every member's share by the extended split rule, marked "Custom" or "Rest"
 * ("Equal" while nobody has a custom amount, or before a total). [editable]:
 * the host taps a row to set that member's amount.
 */
@Composable
private fun SharesList(state: PartyUiState, editable: Boolean, onEdit: (String) -> Unit) {
    val anyCustom = state.shares.any { it.isCustom }
    // Listed on every phone once the host set a custom amount (whatever this phone's switch),
    // and on the host's phone while its "Custom shares" switch is on, so amounts can be set.
    if (state.shares.isEmpty() || !(editable || anyCustom)) return
    val enabled = !state.saving
    Column(Modifier.fillMaxWidth().testTag(PartyTestTags.SHARES), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.shares_title), style = Type.Amount, color = Brand.Ink)
        if (editable) {
            Text(stringResource(R.string.shares_edit_hint), style = Type.Body, color = Brand.InkSecondary)
        }
        state.shares.forEach { share ->
            ShareRow(share, hasTotal = state.total != null, anyCustom = anyCustom, editable = editable, enabled = enabled, onEdit = onEdit)
        }
        state.sharesProblem?.let { SharesProblemLine(it, isHost = state.isHost) }
    }
}

@Composable
private fun ShareRow(
    share: MemberShareUi,
    hasTotal: Boolean,
    anyCustom: Boolean,
    editable: Boolean,
    enabled: Boolean,
    onEdit: (String) -> Unit
) {
    val name = if (share.isMe) stringResource(R.string.shares_you, share.firstName) else share.firstName
    val marker = shareMarker(share, hasTotal, anyCustom)
    val amount = share.shareCents ?: share.customCents
    val spoken = shareDescription(share, name, hasTotal)
    val editLabel = stringResource(R.string.shares_edit_action, share.firstName)
    val rowModifier = if (editable) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClickLabel = editLabel,
            onClick = { onEdit(share.userId) }
        )
    } else {
        Modifier
    }
    Row(
        // Tag and tap action stay on the row; its texts read as the one [spoken] label.
        modifier = Modifier
            .testTag(PartyTestTags.share(share.userId))
            .fillMaxWidth()
            .then(rowModifier)
            .padding(vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = Type.Subtitle, color = Brand.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.background(
                if (share.isCustom) Brand.Highlight else Brand.Chip,
                CircleShape
            ).padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(marker, style = Type.Body, color = if (share.isCustom) Brand.Blue else Brand.InkSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            amount?.let { Money.format(it) } ?: stringResource(R.string.shares_pending),
            style = Type.Amount,
            color = if (editable) Brand.Blue else Brand.Ink
        )
    }
}

@Composable
private fun shareMarker(share: MemberShareUi, hasTotal: Boolean, anyCustom: Boolean): String = when {
    share.isCustom -> stringResource(R.string.shares_custom)
    anyCustom && hasTotal -> stringResource(R.string.shares_remainder)
    else -> stringResource(R.string.shares_equal)
}

/** TalkBack: the member's name, whether they are on a custom or remainder share, and the amount. */
@Composable
private fun shareDescription(share: MemberShareUi, name: String, hasTotal: Boolean): String {
    val custom = share.customCents
    val amount = share.shareCents
    return when {
        custom != null && hasTotal -> stringResource(R.string.shares_custom_description, name, Money.spoken(custom))
        custom != null -> stringResource(R.string.shares_custom_no_total_description, name, Money.spoken(custom))
        !hasTotal -> stringResource(R.string.shares_equal_description, name)
        amount == null -> stringResource(R.string.shares_remainder_pending_description, name)
        else -> stringResource(R.string.shares_remainder_description, name, Money.spoken(amount))
    }
}

/** Shown on every phone while the custom amounts do not fit; the host gets what to do about it. */
@Composable
private fun SharesProblemLine(problem: SharesProblemUi, isHost: Boolean) {
    val text = when {
        !isHost -> stringResource(R.string.shares_problem_member)

        problem.problem == CustomShareProblem.OverTotal ->
            stringResource(R.string.shares_over_total_host, Money.format(problem.customCents), Money.format(problem.totalCents))

        else -> stringResource(R.string.shares_unassigned_host, Money.format(problem.customCents), Money.format(problem.totalCents))
    }
    Text(
        text,
        style = Type.Body,
        color = if (isHost) Brand.Error else Brand.InkSecondary,
        modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag(PartyTestTags.SHARES_PROBLEM)
    )
}

@Composable
private fun TotalCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerSpacing)
    ) { content() }
}

/** The card's own spacing, for parts that put several items in it. */
@Composable
private fun CardStack(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerSpacing)
    ) { content() }
}

@Composable
private fun Waiting(modifier: Modifier = Modifier) {
    CardStack(modifier) {
        Text(stringResource(R.string.party_waiting_for_total), style = Type.SectionTitle, color = Brand.Ink)
        Text(
            stringResource(R.string.party_waiting_for_total_hint),
            style = Type.Body,
            color = Brand.InkSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddTheTotal(enabled: Boolean, actions: TotalActions, modifier: Modifier = Modifier) = CardStack(modifier) {
    Text(stringResource(R.string.total_add_title), style = Type.SectionTitle, color = Brand.Ink)
    Text(stringResource(R.string.total_add_hint), style = Type.Body, color = Brand.InkSecondary, textAlign = TextAlign.Center)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            stringResource(R.string.total_scan),
            onClick = actions::scan,
            enabled = enabled,
            modifier = Modifier.testTag(PartyTestTags.SCAN)
        )
        SoftPillButton(
            stringResource(R.string.total_enter),
            onClick = actions::enterTotal,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(PartyTestTags.ENTER)
        )
    }
}

/** "Your share" with the rolling amount, then Total, Tip and People rows; all from one [TotalUi]. */
@Composable
private fun ShareFigures(total: TotalUi, modifier: Modifier = Modifier) = CardStack(modifier) {
    val share = total.shareCents
    Column(
        Modifier.fillMaxWidth().testTag(PartyTestTags.SHARE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (share == null && total.sharePending) {
            Text(stringResource(R.string.total_your_share), style = Type.Body, color = Brand.InkSecondary)
            Text(
                stringResource(R.string.total_share_pending),
                style = Type.Body,
                color = Brand.InkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else if (share != null) {
            val spoken = stringResource(R.string.total_your_share_description, Money.spoken(share))
            Text(stringResource(R.string.total_your_share), style = Type.Body, color = Brand.InkSecondary)
            RollingAmount(
                share,
                Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                }
            )
        } else {
            Text(stringResource(R.string.total_not_in_party), style = Type.Body, color = Brand.InkSecondary)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        // The total rolls with the share (as on iOS) when the bill or the tip changes.
        FigureRow(
            stringResource(R.string.total_total),
            stringResource(R.string.total_total_description, Money.spoken(total.totalCents)),
            Modifier.testTag(PartyTestTags.TOTAL_VALUE)
        ) { RollingAmount(total.totalCents, style = Type.Amount) }
        val tipLabel = tipLabel(total.tip)
        FigureRow(
            stringResource(R.string.total_tip),
            stringResource(R.string.total_tip_value, tipLabel, Money.format(total.tipCents)),
            stringResource(R.string.total_tip_description, tipLabel, Money.spoken(total.tipCents))
        )
        val people = if (total.headcount == 1) {
            stringResource(R.string.total_one_person_description)
        } else {
            stringResource(R.string.total_people_description, total.headcount)
        }
        FigureRow(
            stringResource(R.string.total_people),
            total.headcount.toString(),
            people,
            Modifier.testTag(PartyTestTags.HEADCOUNT)
        )
    }
}

@Composable
private fun tipLabel(tip: TipChoice): String = when (tip) {
    TipChoice.Zero -> stringResource(R.string.tip_none)
    is TipChoice.Percent -> stringResource(R.string.tip_percent, tip.percent)
    is TipChoice.Custom -> stringResource(R.string.tip_custom)
}

@Composable
private fun FigureRow(label: String, value: String, spoken: String, modifier: Modifier = Modifier) {
    FigureRow(label, spoken, modifier) { Text(value, style = Type.Amount, color = Brand.Ink) }
}

@Composable
private fun FigureRow(label: String, spoken: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = Type.Subtitle, color = Brand.InkSecondary, modifier = Modifier.weight(1f))
        value()
    }
}

/** 0%, 15%, 18%, 20%, Custom: one selectable group; black when chosen. No Material chip styling or ripple. */
@Composable
private fun TipChips(selected: TipChoice, enabled: Boolean, actions: TotalActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tip_title), style = Type.Amount, color = Brand.Ink)
        Row(
            Modifier.fillMaxWidth().selectableGroup().testTag(PartyTestTags.TIP_CHIPS),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chipModifier = Modifier.weight(1f)
            TipChip(stringResource(R.string.tip_none), selected == TipChoice.Zero, enabled, {
                actions.selectTip(TipChoice.Zero)
            }, chipModifier)
            TipChoice.PRESET_PERCENTS.forEach { percent ->
                TipChip(
                    stringResource(R.string.tip_percent, percent),
                    selected == TipChoice.Percent(percent),
                    enabled,
                    { actions.selectTip(TipChoice.Percent(percent)) },
                    chipModifier
                )
            }
            val custom = selected as? TipChoice.Custom
            TipChip(
                custom?.let { Money.format(it.amountCents) } ?: stringResource(R.string.tip_custom),
                custom != null,
                enabled,
                actions::customTip,
                Modifier.weight(CUSTOM_CHIP_WEIGHT)
            )
        }
    }
}

private const val CUSTOM_CHIP_WEIGHT = 1.6f

@Composable
private fun TipChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.tip_chip_description, label)
    Box(
        modifier = modifier
            .height(Dimens.ChipHeight)
            .background(if (selected) Color.Black else Brand.Chip, CircleShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = spoken }
            .testTag(PartyTestTags.tipChip(label)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Type.ChipCount, color = if (selected) Color.White else Brand.Ink, maxLines = 1)
    }
}

@Composable
private fun ChangeTotal(enabled: Boolean, actions: TotalActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoftPillButton(
            stringResource(R.string.total_scan),
            onClick = actions::scan,
            enabled = enabled,
            height = Dimens.ChipHeight,
            style = Type.ChipCount,
            modifier = Modifier.weight(1f).testTag(PartyTestTags.SCAN)
        )
        SoftPillButton(
            stringResource(R.string.total_change),
            onClick = actions::enterTotal,
            enabled = enabled,
            height = Dimens.ChipHeight,
            style = Type.ChipCount,
            modifier = Modifier.weight(1f).testTag(PartyTestTags.ENTER)
        )
    }
}

@Composable
private fun WriteErrorLine(error: WriteError) {
    val text = writeErrorText(error)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }.testTag(PartyTestTags.WRITE_ERROR),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_exclamationmark_circle_fill),
            contentDescription = null,
            tint = Brand.Error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.Body, color = Brand.Error)
    }
}
