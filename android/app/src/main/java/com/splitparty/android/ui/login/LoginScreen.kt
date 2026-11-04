package com.splitparty.android.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.R
import com.splitparty.android.ui.components.BrandMark
import com.splitparty.android.ui.components.PillButton
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.SplitPartyTheme
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.bottomInset
import com.splitparty.android.ui.theme.topInset
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginRoute(modifier: Modifier = Modifier, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Layout of docs/design/ios-captures/login-*.png: the mark, wordmark and form centred
 * in the space above the keyboard, and the database footer at the bottom. The
 * field takes focus on open, as on iOS.
 */
@Composable
fun LoginScreen(state: LoginUiState, onAction: (LoginAction) -> Unit, modifier: Modifier = Modifier, focusOnAppear: Boolean = true) {
    // The keyboard replaces the bottom inset, as iOS's keyboard safe area does.
    val bottom = max(WindowInsets.ime.asPaddingValues().calculateBottomPadding(), bottomInset())
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brand.Page)
            .padding(top = topInset(), bottom = bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(size = Dimens.LoginMark)
        Spacer(Modifier.height(Dimens.LoginMarkGap))
        Text("Split Party", style = Type.Wordmark, color = Brand.Blue)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with your username", style = Type.Subtitle, color = Brand.InkSecondary)
        Spacer(Modifier.height(32.dp))
        Column(Modifier.padding(horizontal = 24.dp)) {
            UsernameField(state, onAction, focusOnAppear)
            state.error?.let { message ->
                Spacer(Modifier.height(12.dp))
                InlineError(message)
            }
            Spacer(Modifier.height(12.dp + 8.dp))
            PillButton(
                text = "Sign in",
                onClick = { onAction(LoginAction.Submit) },
                enabled = state.canSubmit,
                busy = state.busy,
                modifier = Modifier.testTag(LoginTestTags.SUBMIT)
            )
        }
        Spacer(Modifier.weight(1f))
        SetupFooter(state.targetSummary, state.setupProblems)
    }
}

@Composable
private fun UsernameField(state: LoginUiState, onAction: (LoginAction) -> Unit, focusOnAppear: Boolean) {
    val focusRequester = remember { FocusRequester() }
    if (focusOnAppear) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val outline = if (state.error != null) Brand.Error else Brand.Card
    BasicTextField(
        value = state.username,
        onValueChange = { onAction(LoginAction.UsernameChanged(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.FieldHeight)
            .focusRequester(focusRequester)
            .testTag(LoginTestTags.USERNAME),
        singleLine = true,
        textStyle = Type.Field.copy(color = Brand.Ink),
        cursorBrush = SolidColor(Brand.Caret),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onAction(LoginAction.Submit) }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brand.Card, CircleShape)
                    .border(ERROR_OUTLINE, outline, CircleShape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (state.username.isEmpty()) Text("Username", style = Type.Field, color = Brand.Placeholder)
                inner()
            }
        }
    )
}

private val ERROR_OUTLINE: Dp = 1.5.dp

/** Filled "!" circle and red text under the field, like the iOS Label. */
@Composable
private fun InlineError(message: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 8.dp).testTag(LoginTestTags.ERROR)) {
        Icon(
            painter = painterResource(R.drawable.ic_exclamationmark_circle_fill),
            contentDescription = null,
            tint = Brand.Error,
            modifier = Modifier.padding(top = 1.dp).size(16.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(message, style = Type.Body, color = Brand.Error)
    }
}

/** Which database the build points at and, when setup is incomplete, what to fix. */
@Composable
private fun SetupFooter(targetSummary: String, problems: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_cylinder),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Brand.InkSecondary),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(targetSummary, style = Type.Footer, color = Brand.InkSecondary)
        }
        problems.forEach { problem ->
            Text(problem, style = Type.Footer, color = Brand.Warning, textAlign = TextAlign.Center)
        }
    }
}

object LoginTestTags {
    const val USERNAME = "login_username"
    const val SUBMIT = "login_submit"
    const val ERROR = "login_error"
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    SplitPartyTheme {
        LoginScreen(LoginUiState(targetSummary = "Emulator 10.0.2.2:8080 (project demo-split-party)"), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    SplitPartyTheme {
        LoginScreen(LoginUiState(username = "thor", error = LoginViewModel.noAccount("thor")), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenNotConfiguredPreview() {
    SplitPartyTheme {
        LoginScreen(
            LoginUiState(
                targetSummary = "Firestore target not configured",
                setupProblems = persistentListOf("FIREBASE_TARGET is not set.")
            ),
            onAction = {}
        )
    }
}
