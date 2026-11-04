package com.splitparty.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Keeps the signed-in username across app restarts until the user logs out. */
class SessionStore(private val context: Context) {
    private val key = stringPreferencesKey("signedInUserId")

    val signedInUserId: Flow<String?> = context.sessionDataStore.data.map { it[key] }

    suspend fun signIn(userId: String) {
        context.sessionDataStore.edit { it[key] = userId }
    }

    suspend fun signOut() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}
