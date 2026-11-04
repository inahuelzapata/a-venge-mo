package com.splitparty.android.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.data.firebase.FirebaseConnection
import com.splitparty.android.ui.gate.LocalNetworkGate
import com.splitparty.android.ui.login.LoginRoute
import com.splitparty.android.ui.setup.SetupRequiredScreen
import com.splitparty.android.ui.shell.MainShell
import com.splitparty.android.ui.theme.Brand
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Picks setup / login / signed-in shell for the configured database. */
@Composable
fun SplitPartyApp(modifier: Modifier = Modifier, connection: FirebaseConnection = koinInject()) {
    Box(modifier.fillMaxSize().background(Brand.Page)) {
        when (connection) {
            is FirebaseConnection.SetupRequired -> SetupRequiredScreen(connection.target, connection.problems)
            is FirebaseConnection.Ready -> LocalNetworkGate(connection.target) { SessionRoot() }
        }
    }
}

/**
 * Login is never on the navigation back stack, so system back from Home can't
 * return to it while signed in. `key(userId)` gives each account fresh screens.
 */
@Composable
private fun SessionRoot(viewModel: SessionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val session = state) {
        SessionState.Restoring -> Unit

        // DataStore answers in a few ms; keep the page background meanwhile.
        SessionState.SignedOut -> LoginRoute()

        is SessionState.SignedIn -> key(session.userId) { MainShell(session.userId) }
    }
}
