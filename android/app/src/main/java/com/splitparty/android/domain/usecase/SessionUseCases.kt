package com.splitparty.android.domain.usecase

import com.splitparty.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

/** The signed-in username, or null when signed out. */
class ObserveSessionUseCase(private val session: SessionRepository) {
    operator fun invoke(): Flow<String?> = session.signedInUserId
}

class SignOutUseCase(private val session: SessionRepository) {
    suspend operator fun invoke() = session.signOut()
}
