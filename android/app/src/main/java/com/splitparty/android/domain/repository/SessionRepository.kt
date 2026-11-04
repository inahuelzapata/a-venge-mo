package com.splitparty.android.domain.repository

import kotlinx.coroutines.flow.Flow

/** The signed-in username, kept across app restarts until the user logs out. */
interface SessionRepository {
    val signedInUserId: Flow<String?>

    suspend fun signIn(userId: String)

    suspend fun signOut()
}
