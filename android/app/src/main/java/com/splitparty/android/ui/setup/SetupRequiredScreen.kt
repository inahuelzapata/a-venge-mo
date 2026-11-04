package com.splitparty.android.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.ui.login.LoginAction
import com.splitparty.android.ui.login.LoginScreen
import com.splitparty.android.ui.login.LoginUiState
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * Shown instead of a working login when the local Firestore setup is incomplete
 * (android/README.md). It looks like docs/design/ios-captures/login-not-configured.png:
 * the login screen with Sign in disabled, and a footer that says what to fix.
 */
@Composable
fun SetupRequiredScreen(target: FirestoreTarget, problems: List<String>, modifier: Modifier = Modifier, focusOnAppear: Boolean = true) {
    var username by rememberSaveable { mutableStateOf("") }
    LoginScreen(
        state = LoginUiState(
            username = username,
            targetSummary = target.summary,
            setupProblems = problems.toImmutableList()
        ),
        onAction = { action -> if (action is LoginAction.UsernameChanged) username = action.value },
        modifier = modifier,
        focusOnAppear = focusOnAppear
    )
}

@Preview(showBackground = true)
@Composable
private fun SetupRequiredScreenPreview() {
    SplitPartyTheme {
        SetupRequiredScreen(
            FirestoreTarget.NotConfigured("FIREBASE_TARGET is not set."),
            listOf("FIREBASE_TARGET is not set.")
        )
    }
}
