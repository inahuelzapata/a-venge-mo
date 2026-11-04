package com.splitparty.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.splitparty.android.data.FirebaseConnection
import com.splitparty.android.data.SessionStore
import com.splitparty.android.data.SplitPartyRepository
import com.splitparty.android.ui.LocalNetworkGate
import com.splitparty.android.ui.MainShell
import com.splitparty.android.ui.SetupRequiredScreen
import com.splitparty.android.ui.login.LoginScreen
import com.splitparty.android.ui.login.LoginViewModel
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SplitPartyApplication).container
        setContent {
            SplitPartyTheme {
                Box(Modifier.fillMaxSize().background(Brand.Page)) {
                    Root(container)
                }
            }
        }
    }
}

sealed interface SessionState {
    data object Restoring : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String) : SessionState
}

class SessionViewModel(private val session: SessionStore) : ViewModel() {
    val state: StateFlow<SessionState> = session.signedInUserId
        .map { id -> if (id == null) SessionState.SignedOut else SessionState.SignedIn(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Restoring)

    fun logOut() {
        viewModelScope.launch { session.signOut() }
    }
}

/**
 * Picks setup / login / signed-in shell. Login is never on the navigation back
 * stack, so system back from Home cannot return to it while signed in.
 */
@Composable
private fun Root(container: AppContainer) {
    val connection = container.connection
    if (connection is FirebaseConnection.SetupRequired) {
        SetupRequiredScreen(connection.target, connection.problems)
        return
    }
    LocalNetworkGate(connection.target) { SignedInOrLogin(container, connection) }
}

@Composable
private fun SignedInOrLogin(container: AppContainer, connection: FirebaseConnection) {
    val repository: SplitPartyRepository = container.repository!!
    val sessionViewModel: SessionViewModel = viewModel(factory = viewModelFactory {
        initializer { SessionViewModel(container.session) }
    })
    val state by sessionViewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Restoring -> Unit // DataStore answers in a few ms; keep the page background meanwhile.
        SessionState.SignedOut -> {
            val loginViewModel: LoginViewModel = viewModel(factory = viewModelFactory {
                initializer { LoginViewModel(repository, container.session) }
            })
            LoginScreen(loginViewModel, connection.target.summary)
        }
        is SessionState.SignedIn -> key(s.userId) {
            MainShell(
                userId = s.userId,
                repository = repository,
                network = container.network,
                onLogOut = sessionViewModel::logOut,
            )
        }
    }
}
