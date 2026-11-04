package com.splitparty.android.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Whether the device has a network at all. Database listeners do not fail when
 * offline (they serve the cache), so screens use this to show their error state
 * right away in airplane mode.
 */
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
}
