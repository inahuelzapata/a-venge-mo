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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.data.SessionStore
import com.splitparty.android.data.SplitPartyRepository
import com.splitparty.android.data.Username
import com.splitparty.android.ui.theme.Brand
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: SplitPartyRepository,
    private val session: SessionStore,
) : ViewModel() {
    var username by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val canSubmit: Boolean get() = username.isNotBlank() && !busy

    fun onUsernameChange(value: String) {
        username = value
        error = null
    }

    fun submit() {
        if (!canSubmit) return
        val id = Username.normalize(username)
        if (!Username.isValid(id)) {
            error = noAccount()
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val user = repository.findUser(id)
                if (user == null) error = noAccount() else session.signIn(user.id)
            } catch (e: TimeoutCancellationException) {
                error = UNREACHABLE
            } catch (e: Exception) {
                error = UNREACHABLE
            } finally {
                busy = false
            }
        }
    }

    private fun noAccount() = "No account named \"${username.trim()}\". Try maxi, annie or christophe."

    private companion object {
        const val UNREACHABLE = "Can't reach the server. Check the connection and try again."
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel, targetSummary: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.Card)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.size(72.dp).background(Brand.Blue, CircleShape),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("Split Party", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
        Spacer(Modifier.height(6.dp))
        Text("Sign in with your username", color = Brand.InkSecondary)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = viewModel.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            singleLine = true,
            isError = viewModel.error != null,
            supportingText = viewModel.error?.let { message -> { Text(message) } },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand.Blue, focusedLabelColor = Brand.Blue),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = viewModel::submit,
            enabled = viewModel.canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Ink, contentColor = Color.White),
        ) {
            if (viewModel.busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Sign in", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(targetSummary, color = Brand.InkSecondary, fontSize = 12.sp)
    }
}
