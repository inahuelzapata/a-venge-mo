package com.splitparty.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.splitparty.android.domain.repository.DemoSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.demoDataStore by preferencesDataStore(name = "demo")

/** [DemoSettingsRepository] in Preferences DataStore; a missing key means on for "at Tony's", off for custom shares. */
class DataStoreDemoSettingsRepository(private val context: Context) : DemoSettingsRepository {
    private val key = booleanPreferencesKey("atMerchant")
    private val customSharesKey = booleanPreferencesKey("customShares")

    override val atMerchant: Flow<Boolean> = context.demoDataStore.data.map { it[key] ?: true }

    override suspend fun setAtMerchant(on: Boolean) {
        context.demoDataStore.edit { it[key] = on }
    }

    override val customShares: Flow<Boolean> = context.demoDataStore.data.map { it[customSharesKey] ?: false }

    override suspend fun setCustomShares(on: Boolean) {
        context.demoDataStore.edit { it[customSharesKey] = on }
    }
}
