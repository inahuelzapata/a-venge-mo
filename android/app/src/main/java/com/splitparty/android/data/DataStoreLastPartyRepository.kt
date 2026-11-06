package com.splitparty.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.splitparty.android.domain.repository.LastPartyRepository
import kotlinx.coroutines.flow.first

private val Context.lastPartyDataStore by preferencesDataStore(name = "last_party")

/** [LastPartyRepository] in Preferences DataStore. */
class DataStoreLastPartyRepository(private val context: Context) : LastPartyRepository {
    private val key = stringPreferencesKey("partyId")

    override suspend fun lastPartyId(): String? = context.lastPartyDataStore.data.first()[key]

    override suspend fun remember(partyId: String) {
        context.lastPartyDataStore.edit { it[key] = partyId }
    }

    override suspend fun forget() {
        context.lastPartyDataStore.edit { it.remove(key) }
    }
}
