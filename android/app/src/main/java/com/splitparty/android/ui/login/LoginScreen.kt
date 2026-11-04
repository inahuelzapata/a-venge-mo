package com.splitparty.android.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginRoute(modifier: Modifier = Modifier, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun LoginScreen(state: LoginUiState, onAction: (LoginAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brand.Card)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.size(72.dp).background(Brand.Blue, CircleShape),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("Split Party", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
        Spacer(Modifier.height(6.dp))
        Text("Sign in with your username", color = Brand.InkSecondary)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = { onAction(LoginAction.UsernameChanged(it)) },
            modifier = Modifier.fillMaxWidth().testTag(LoginTestTags.USERNAME),
            label = { Text("Username") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { message -> { Text(message) } },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand.Blue, focusedLabelColor = Brand.Blue),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onAction(LoginAction.Submit) })
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAction(LoginAction.Submit) },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag(LoginTestTags.SUBMIT),
            shape = RoundedCornerShape(28.dp),
            // While busy the button is disabled but stays dark, so the white spinner is visible.
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Ink, contentColor = Color.White).let { colors ->
                if (state.busy) colors.copy(disabledContainerColor = Brand.Ink, disabledContentColor = Color.White) else colors
            }
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Sign in", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(state.targetSummary, color = Brand.InkSecondary, fontSize = 12.sp)
    }
}

object LoginTestTags {
    const val USERNAME = "login_username"
    const val SUBMIT = "login_submit"
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
