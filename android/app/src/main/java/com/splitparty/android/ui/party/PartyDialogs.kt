package com.splitparty.android.ui.party

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.splitparty.android.R
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.SoftPillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type

/**
 * A confirmation in the app's own card style (no Material AlertDialog): title,
 * body, a black action and a grey cancel. Back or a tap outside cancels.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: WriteError? = null,
    errorText: String? = null
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        ConfirmCard(title, body, confirmLabel, cancelLabel, onConfirm, onDismiss, modifier, busy, error, errorText)
    }
}

/** The dialog's card on its own (screenshots render it without a dialog window). */
@Composable
fun ConfirmCard(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: WriteError? = null,
    /** Shown instead of [error]'s text, for failures that are not party writes. */
    errorText: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .padding(Dimens.CardPadding)
            .semantics { paneTitle = title }
            .testTag(PartyTestTags.DIALOG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerSpacing)
    ) {
        Text(title, style = Type.SectionTitle, color = Brand.Ink, textAlign = TextAlign.Center)
        Text(body, style = Type.Body, color = Brand.InkSecondary, textAlign = TextAlign.Center)
        val errorMessage = errorText ?: error?.let {
            writeErrorText(it)
        }
        if (errorMessage != null) {
            Text(
                errorMessage,
                style = Type.Body,
                color = Brand.Error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                confirmLabel,
                onClick = onConfirm,
                busy = busy,
                enabled = !busy,
                modifier = Modifier.testTag(PartyTestTags.DIALOG_CONFIRM)
            )
            SoftPillButton(cancelLabel, onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
    }
}
