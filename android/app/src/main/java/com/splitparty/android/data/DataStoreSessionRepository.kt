package com.splitparty.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.splitparty.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** [SessionRepository] in Preferences DataStore. */
class DataStoreSessionRepository(private val context: Context) : SessionRepository {
    private val key = stringPreferencesKey("signedInUserId")

    override val signedInUserId: Flow<String?> = context.sessionDataStore.data.map { it[key] }

    override suspend fun signIn(userId: String) {
        context.sessionDataStore.edit { it[key] = userId }
    }

    override suspend fun signOut() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}
