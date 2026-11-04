package com.splitparty.android.domain.usecase

import com.splitparty.android.domain.model.UserLookup
import com.splitparty.android.domain.model.Username
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository

sealed interface SignInResult {
    data class Success(val userId: String) : SignInResult

    /** No `users/{id}` document, or the input can't be a username at all. */
    data class UnknownUser(val input: String) : SignInResult

    data object Unreachable : SignInResult
}

/** Username-only login: case and surrounding spaces are ignored; success persists the session. */
class SignInUseCase(private val repository: SplitPartyRepository, private val session: SessionRepository) {
    suspend operator fun invoke(rawUsername: String): SignInResult {
        val id = Username.normalize(rawUsername)
        val unknown = SignInResult.UnknownUser(rawUsername.trim())
        if (!Username.isValid(id)) return unknown
        return when (val lookup = repository.findUser(id)) {
            is UserLookup.Found -> {
                session.signIn(lookup.user.id)
                SignInResult.Success(lookup.user.id)
            }

            UserLookup.NotFound -> unknown

            UserLookup.Unreachable -> SignInResult.Unreachable
        }
    }
}
