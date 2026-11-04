package com.splitparty.android.ui.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.domain.usecase.SignInResult
import com.splitparty.android.domain.usecase.SignInUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LoginUiState(
    val username: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    /** Which database this build points at, shown small under the form. */
    val targetSummary: String = "",
    /** What to fix when the build has no usable database; sign-in stays disabled meanwhile. */
    val setupProblems: ImmutableList<String> = persistentListOf()
) {
    val canSubmit: Boolean get() = username.isNotBlank() && !busy && setupProblems.isEmpty()
}

sealed interface LoginAction {
    data class UsernameChanged(val value: String) : LoginAction

    data object Submit : LoginAction
}

/** Username-only login. A successful sign-in updates the session, which swaps this screen for the shell. */
class LoginViewModel(private val signIn: SignInUseCase, target: FirestoreTarget) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState(targetSummary = target.summary))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.UsernameChanged -> _state.update { it.copy(username = action.value, error = null) }
            LoginAction.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = when (val result = signIn(current.username)) {
                is SignInResult.Success -> null
                is SignInResult.UnknownUser -> noAccount(result.input)
                SignInResult.Unreachable -> UNREACHABLE
            }
            _state.update { it.copy(busy = false, error = error) }
        }
    }

    companion object {
        const val UNREACHABLE = "Can't reach the database. Check your connection and try again."

        fun noAccount(input: String) = "No account called \"$input\". Try Maxi, Annie or Christophe."
    }
}
