package com.splitparty.android.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitparty.android.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    data object Restoring : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val userId: String) : SessionState
}

/** Which top-level screen to show: login or the signed-in shell. */
class SessionViewModel(observeSession: ObserveSessionUseCase) : ViewModel() {
    val state: StateFlow<SessionState> = observeSession()
        .map { id -> if (id == null) SessionState.SignedOut else SessionState.SignedIn(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Restoring)
}
