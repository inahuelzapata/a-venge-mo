package com.splitparty.android.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Demo switches kept on the phone across restarts. "At Tony's" stands in for
 * the geofence match (PoC-7): on by default.
 */
interface DemoSettingsRepository {
    val atMerchant: Flow<Boolean>

    suspend fun setAtMerchant(on: Boolean)
}
