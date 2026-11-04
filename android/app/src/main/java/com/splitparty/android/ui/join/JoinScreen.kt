package com.splitparty.android.ui.join

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.domain.party.JoinCodes
import com.splitparty.android.ui.components.ActivitySpinner
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.party.EmojiCodeTiles
import com.splitparty.android.ui.party.PartyHeader
import com.splitparty.android.ui.party.partyHaptic
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.LocalReduceMotion
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.topInset
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun JoinRoute(
    partyId: String,
    userId: String,
    contentPadding: PaddingValues,
    onOpenParty: (partyId: String) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinViewModel = koinViewModel(key = "join_$partyId") { parametersOf(partyId, userId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val openParty by rememberUpdatedState(onOpenParty)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is JoinEvent.Joined -> {
                    view.partyHaptic(success = true)
                    openParty(event.partyId)
                }

                JoinEvent.WrongCode -> view.partyHaptic(success = false)
            }
        }
    }
    JoinScreen(state, contentPadding, onPick = viewModel::pick, onBack = onHome, modifier = modifier)
}

/**
 * Code confirmation (PoC-9): "Join Annie's party", four code options as
 * white cards, one of them the host's. Provisional layout
 * (docs/design/visual-conventions.md, "Code confirmation").
 */
@Composable
fun JoinScreen(
    state: JoinUiState,
    contentPadding: PaddingValues,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset() + Dimens.HeaderTop, bottom = contentPadding.calculateBottomPadding())
    ) {
        PartyHeader(stringResource(R.string.join_title, state.hostName), onBack)
        when (state.phase) {
            JoinPhase.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { ActivitySpinner() }

            JoinPhase.Choosing -> Choosing(state, onPick, Modifier.weight(1f))

            JoinPhase.Locked -> Ended(
                stringResource(R.string.join_locked_title),
                stringResource(R.string.join_locked_body, state.hostName),
                onBack,
                Modifier.testTag(JoinTestTags.LOCKED)
            )

            JoinPhase.Unavailable -> Ended(stringResource(R.string.join_unavailable), "", onBack)
        }
    }
}

@Composable
private fun Choosing(state: JoinUiState, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = Dimens.ContentSpacing, bottom = Dimens.ListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        Text(
            stringResource(R.string.join_prompt, state.hostName),
            style = Type.Subtitle,
            color = Brand.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        val message = when {
            state.showWrongCode -> stringResource(R.string.join_wrong)
            state.joinFailed -> stringResource(R.string.join_failed)
            else -> null
        }
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            if (message != null) {
                Text(
                    message,
                    style = Type.Body,
                    color = Brand.Error,
                    modifier = Modifier.testTag(JoinTestTags.ERROR).semantics { liveRegion = LiveRegionMode.Assertive }
                )
            } else if (state.busy && state.wrongPick == null) {
                ActivitySpinner()
            }
        }
        state.options.forEachIndexed { index, option ->
            CodeOption(
                code = option,
                wrong = state.wrongPick == index,
                shakeKey = state.wrongPickCount,
                enabled = !state.busy,
                onClick = { onPick(index) },
                modifier = Modifier.testTag(JoinTestTags.option(index))
            )
        }
    }
}

/** A white card of three tiles. A wrong pick shakes it sideways (instantly red-outlined without motion). */
@Composable
private fun CodeOption(
    code: List<String>,
    wrong: Boolean,
    shakeKey: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduce = LocalReduceMotion.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong, shakeKey) {
        if (wrong && !reduce) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 360
                    -14f at 45
                    12f at 105
                    -9f at 165
                    6f at 225
                    -3f at 285
                }
            )
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spoken = JoinCodes.spokenName(code)
    Box(
        modifier = modifier
            .padding(horizontal = Dimens.Gutter)
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value * density }
            .alpha(if (pressed) 0.8f else 1f)
            .background(Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .border(2.dp, if (wrong) Brand.Error else Brand.Card, RoundedCornerShape(Dimens.CardRadius))
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        EmojiCodeTiles(code, tileSize = 60.dp, animated = false)
    }
}

@Composable
private fun Ended(title: String, body: String, onHome: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = Type.PlaceholderTitle, color = Brand.Ink, textAlign = TextAlign.Center)
        if (body.isNotEmpty()) Text(body, style = Type.Subtitle, color = Brand.InkSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        PillButton(stringResource(R.string.party_back_home), onClick = onHome)
    }
}

object JoinTestTags {
    const val LOCKED = "join_locked"
    const val ERROR = "join_error"

    fun option(index: Int) = "join_option_$index"
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7)
@Composable
private fun JoinScreenPreview() {
    SplitPartyTheme {
        JoinScreen(
            JoinUiState(
                phase = JoinPhase.Choosing,
                hostName = "Annie",
                options = persistentListOf(
                    persistentListOf("🍕", "🍺", "🎉"),
                    persistentListOf("🌮", "🔥", "🐶"),
                    persistentListOf("🍣", "🍺", "🚀"),
                    persistentListOf("🎸", "🍩", "🌵")
                )
            ),
            PaddingValues(),
            onPick = {},
            onBack = {}
        )
    }
}
