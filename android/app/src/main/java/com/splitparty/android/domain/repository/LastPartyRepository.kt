package com.splitparty.android.domain.repository

/**
 * The party screen this phone last showed, kept across app restarts, so a
 * relaunch can show how a party ended while the app was closed (cancelled by
 * the host) instead of plain Home.
 */
interface LastPartyRepository {
    suspend fun lastPartyId(): String?

    suspend fun remember(partyId: String)

    suspend fun forget()

    companion object {
        /** Remembers nothing (tests and previews that do not care). */
        val None = object : LastPartyRepository {
            override suspend fun lastPartyId(): String? = null

            override suspend fun remember(partyId: String) = Unit

            override suspend fun forget() = Unit
        }
    }
}
