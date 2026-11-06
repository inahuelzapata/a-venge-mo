package com.splitparty.android.ui.party

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
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
import com.splitparty.android.domain.split.AmountError
import com.splitparty.android.domain.split.AmountParse
import com.splitparty.android.domain.split.CentsInput
import com.splitparty.android.format.Money
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.components.SoftPillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset

/**
 * Where the host is in adding or changing the total. Null = the party screen.
 * These are screens over the party (not nav routes), so system back from any
 * of them returns to the party with nothing written.
 */
sealed interface TotalStep {
    /** Keypad for the bill, optionally pre-filled, optionally explaining why it opened (no total found). */
    data class EnterBill(val prefillCents: Long? = null, val notice: EntryNotice? = null) : TotalStep

    /** Keypad for a custom tip in dollars and cents (0 allowed). */
    data class EnterTip(val prefillCents: Long? = null) : TotalStep

    /** Keypad for one member's custom share (host, "Custom shares" switch on), with a clear back to the equal split. */
    data class EnterShare(val memberId: String, val prefillCents: Long? = null) : TotalStep

    /** The camera and recognition. */
    data object Scan : TotalStep

    /** A scanned amount waiting for the host: confirm or edit. Never written before that. */
    data class ConfirmScan(val cents: Long, val line: String) : TotalStep

    companion object {
        /** Saves the step across configuration changes and process death (strings only). */
        val Saver: Saver<TotalStep?, String> = Saver(
            save = { it?.encode() ?: "" },
            restore = { decode(it) }
        )

        private fun TotalStep.encode(): String = when (this) {
            is EnterBill -> "bill|${prefillCents ?: ""}|${notice?.name ?: ""}"
            is EnterTip -> "tip|${prefillCents ?: ""}"
            is EnterShare -> "share|${prefillCents ?: ""}|$memberId"
            Scan -> "scan"
            is ConfirmScan -> "confirm|$cents|$line"
        }

        private fun decode(text: String): TotalStep? {
            val parts = text.split('|', limit = 3)
            return when (parts[0]) {
                "bill" -> EnterBill(
                    parts.getOrNull(1)?.toLongOrNull(),
                    parts.getOrNull(2)?.let { n ->
                        EntryNotice.entries.firstOrNull {
                            it.name ==
                                n
                        }
                    }
                )

                "tip" -> EnterTip(parts.getOrNull(1)?.toLongOrNull())

                "share" -> parts.getOrNull(2)?.let { EnterShare(it, parts.getOrNull(1)?.toLongOrNull()) }

                "scan" -> Scan

                "confirm" -> parts.getOrNull(1)?.toLongOrNull()?.let { ConfirmScan(it, parts.getOrNull(2).orEmpty()) }

                else -> null
            }
        }
    }
}

/** Why the keypad opened instead of a confirmation. */
enum class EntryNotice { NoTotalFound, Unreadable, CameraDenied, NoCamera }

@Composable
fun rememberTotalStep() = rememberSaveable(stateSaver = TotalStep.Saver) { mutableStateOf<TotalStep?>(null) }

/**
 * The step's screen, over the party screen. [onDone] closes it (back, or the
 * write landed). The scan screen is provided by the caller so tests can swap it.
 */
@Composable
fun TotalEntryOverlay(
    step: TotalStep,
    state: PartyUiState,
    contentPadding: PaddingValues,
    onStep: (TotalStep?) -> Unit,
    onConfirmBill: (Long) -> Unit,
    onConfirmTip: (Long) -> Unit,
    scanScreen: @Composable (onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onConfirmShare: (memberId: String, cents: Long?) -> Unit = { _, _ -> }
) {
    BackHandler { onStep(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Brand.Page)
            // Swallows taps so nothing on the party screen below reacts. Not `clickable`:
            // that would merge every child into one TalkBack element.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = topInset() + Dimens.HeaderTop, bottom = contentPadding.calculateBottomPadding())
    ) {
        val close = { onStep(null) }
        when (step) {
            is TotalStep.EnterBill -> KeypadEntry(
                title = stringResource(R.string.entry_bill_title),
                prefill = step.prefillCents,
                notice = step.notice,
                minCents = CentsInput.MIN_CENTS,
                saving = state.saving,
                writeError = state.writeError,
                onBack = close,
                onConfirm = onConfirmBill
            )

            is TotalStep.EnterTip -> KeypadEntry(
                title = stringResource(R.string.entry_tip_title),
                prefill = step.prefillCents,
                notice = null,
                minCents = 0L,
                saving = state.saving,
                writeError = state.writeError,
                onBack = close,
                onConfirm = onConfirmTip
            )

            is TotalStep.EnterShare -> {
                val name = state.shares.firstOrNull { it.userId == step.memberId }?.firstName.orEmpty()
                KeypadEntry(
                    title = stringResource(R.string.entry_share_title, name),
                    prefill = step.prefillCents,
                    notice = null,
                    // $0.00 is a valid custom share: that member pays nothing.
                    minCents = 0L,
                    saving = state.saving,
                    writeError = state.writeError,
                    onBack = close,
                    onConfirm = { onConfirmShare(step.memberId, it) },
                    onClear = { onConfirmShare(step.memberId, null) }
                )
            }

            TotalStep.Scan -> scanScreen(close)

            is TotalStep.ConfirmScan -> ConfirmScanned(
                step,
                saving = state.saving,
                writeError = state.writeError,
                onBack = close,
                onConfirm = { onConfirmBill(step.cents) },
                onEdit = { onStep(TotalStep.EnterBill(prefillCents = step.cents)) }
            )
        }
    }
}

private const val MAX_INPUT_CHARS = 10

/**
 * Amount display over a custom numeric keypad (no system keyboard). The amount
 * is validated as integer cents when the host taps Confirm; anything outside
 * the range, empty or with more than two decimals shows an inline message and
 * writes nothing.
 */
@Composable
fun KeypadEntry(
    title: String,
    prefill: Long?,
    notice: EntryNotice?,
    minCents: Long,
    saving: Boolean,
    writeError: WriteError?,
    onBack: () -> Unit,
    onConfirm: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, a "Back to equal split" button under Confirm (custom shares). */
    onClear: (() -> Unit)? = null
) {
    var text by rememberSaveable { mutableStateOf(prefill?.let(CentsInput::toInput).orEmpty()) }
    var error by rememberSaveable { mutableStateOf<AmountError?>(null) }
    Column(modifier.fillMaxSize()) {
        PartyHeader(title, onBack)
        Column(
            // Scrolls only on short screens, so the notice and message are never cut off.
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.HeaderMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            notice?.let {
                Text(
                    noticeText(it),
                    style = Type.Body,
                    color = Brand.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(EntryTestTags.NOTICE)
                )
            }
            TypedAmount(text)
            EntryMessage(error?.let { amountErrorText(it, minCents) } ?: writeError?.let { writeErrorText(it) })
        }
        Keypad(
            onKey = { key ->
                error = null
                text = when (key) {
                    KEY_DELETE -> text.dropLast(1)
                    else -> if (text.length < MAX_INPUT_CHARS) text + key else text
                }
            },
            modifier = Modifier.padding(horizontal = Dimens.HeaderMargin)
        )
        Spacer(Modifier.height(12.dp))
        PillButton(
            stringResource(R.string.entry_confirm),
            onClick = {
                when (val parsed = CentsInput.parse(text, min = minCents)) {
                    is AmountParse.Valid -> onConfirm(parsed.cents)
                    is AmountParse.Rejected -> error = parsed.error
                }
            },
            busy = saving,
            enabled = !saving,
            modifier = Modifier.padding(horizontal = Dimens.HeaderMargin).testTag(EntryTestTags.CONFIRM)
        )
        if (onClear != null) {
            Spacer(Modifier.height(10.dp))
            SoftPillButton(
                stringResource(R.string.entry_share_clear),
                onClick = onClear,
                enabled = !saving,
                modifier = Modifier.padding(horizontal = Dimens.HeaderMargin).fillMaxWidth().testTag(EntryTestTags.CLEAR)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The typed text as an amount ("$48.5" while typing), read out as dollars and cents. */
@Composable
private fun TypedAmount(text: String, modifier: Modifier = Modifier) {
    val shown = if (text.isEmpty()) "$0.00" else "$$text"
    val spoken = (CentsInput.parse(text, min = 0) as? AmountParse.Valid)?.let { Money.spoken(it.cents) } ?: shown
    Text(
        shown,
        style = Type.Balance,
        color = if (text.isEmpty()) Brand.Placeholder else Brand.Ink,
        modifier = modifier.testTag(EntryTestTags.AMOUNT).clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        }
    )
}

/** A fixed 40-tall line for the inline message, so the keypad never jumps when one appears. */
@Composable
private fun EntryMessage(message: String?, modifier: Modifier = Modifier) {
    Box(modifier.height(40.dp), contentAlignment = Alignment.Center) {
        if (message != null) {
            Text(
                message,
                style = Type.Body,
                color = Brand.Error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(EntryTestTags.ERROR).semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
    }
}

const val KEY_DELETE = "⌫"
private val KEY_ROWS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", KEY_DELETE))

@Composable
private fun Keypad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEY_ROWS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> KeypadKey(key, { onKey(key) }, Modifier.weight(1f)) }
            }
        }
    }
}

private const val PRESSED_ALPHA = 0.5f

@Composable
private fun KeypadKey(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val description = when (key) {
        KEY_DELETE -> stringResource(R.string.entry_key_delete)
        "." -> stringResource(R.string.entry_key_decimal)
        else -> key
    }
    Box(
        modifier = modifier
            .height(56.dp)
            .alpha(if (pressed) PRESSED_ALPHA else 1f)
            .background(Brand.Card, RoundedCornerShape(16.dp))
            .testTag(EntryTestTags.key(key))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(key, style = Type.ScreenTitle, color = Brand.Ink)
    }
}

/** The scanned amount, shown big with the line it came from; Confirm writes it, Edit opens the keypad. */
@Composable
private fun ConfirmScanned(
    step: TotalStep.ConfirmScan,
    saving: Boolean,
    writeError: WriteError?,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PartyHeader(stringResource(R.string.confirm_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = Dimens.HeaderMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text(stringResource(R.string.confirm_found), style = Type.Body, color = Brand.InkSecondary)
            val spoken = Money.spoken(step.cents)
            Text(
                Money.format(step.cents),
                style = Type.Balance,
                color = Brand.Ink,
                modifier = Modifier.testTag(EntryTestTags.AMOUNT).clearAndSetSemantics { contentDescription = spoken }
            )
            if (step.line.isNotBlank()) {
                Text(
                    stringResource(R.string.confirm_line, step.line),
                    style = Type.Body,
                    color = Brand.InkSecondary,
                    textAlign = TextAlign.Center
                )
            }
            writeError?.let {
                Text(writeErrorText(it), style = Type.Body, color = Brand.Error, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(horizontal = Dimens.HeaderMargin), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                stringResource(R.string.confirm_action),
                onClick = onConfirm,
                busy = saving,
                enabled = !saving,
                modifier = Modifier.testTag(EntryTestTags.CONFIRM)
            )
            SoftPillButton(
                stringResource(R.string.confirm_edit),
                onClick = onEdit,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag(EntryTestTags.EDIT)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun noticeText(notice: EntryNotice): String = when (notice) {
    EntryNotice.NoTotalFound -> stringResource(R.string.entry_notice_no_total)
    EntryNotice.Unreadable -> stringResource(R.string.entry_notice_unreadable)
    EntryNotice.CameraDenied -> stringResource(R.string.scan_camera_denied)
    EntryNotice.NoCamera -> stringResource(R.string.scan_no_camera)
}

@Composable
private fun amountErrorText(error: AmountError, minCents: Long): String = when (error) {
    AmountError.Empty -> stringResource(R.string.entry_error_empty)
    AmountError.TooManyDecimals -> stringResource(R.string.entry_error_decimals)
    AmountError.Invalid -> stringResource(R.string.entry_error_invalid)
    AmountError.BelowMinimum -> stringResource(R.string.entry_error_min, Money.format(minCents.coerceAtLeast(CentsInput.MIN_CENTS)))
    AmountError.AboveMaximum -> stringResource(R.string.entry_error_max, Money.format(CentsInput.MAX_CENTS))
}

@Composable
internal fun writeErrorText(error: WriteError): String = when (error) {
    WriteError.Failed -> stringResource(R.string.write_failed)
    WriteError.NotAllowed -> stringResource(R.string.write_not_allowed)
    WriteError.SharesOverTotal -> stringResource(R.string.write_shares_over_total)
    WriteError.SharesUnassigned -> stringResource(R.string.write_shares_unassigned)
}

object EntryTestTags {
    const val AMOUNT = "entry_amount"
    const val ERROR = "entry_error"
    const val NOTICE = "entry_notice"
    const val CONFIRM = "entry_confirm"
    const val EDIT = "entry_edit"
    const val CLEAR = "entry_clear"

    fun key(key: String) = "entry_key_$key"
}
