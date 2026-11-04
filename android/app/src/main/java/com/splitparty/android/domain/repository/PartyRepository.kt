package com.splitparty.android.domain.repository

import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import kotlinx.coroutines.flow.Flow

/** Outcome of a create attempt. A failure has written nothing (one Firestore transaction). */
sealed interface CreatePartyResult {
    data class Created(val partyId: String) : CreatePartyResult

    data object Failed : CreatePartyResult
}

sealed interface JoinPartyResult {
    data object Joined : JoinPartyResult

    /** Expired, settled, full or locked out by the time the write ran. */
    data object NotJoinable : JoinPartyResult

    /** No connection or the write was rejected. */
    data object Failed : JoinPartyResult
}

/** Split Party reads and writes (docs/firestore-schema.md, `parties` and `joinCodes`). */
interface PartyRepository {
    /**
     * Parties at [merchantId] stored as `open` whose `expiresAt` was still
     * ahead when the listener started. Callers filter by the clock again.
     */
    fun openParties(merchantId: String): Flow<LiveEvent<List<Party>>>

    /** `parties/{partyId}`; null when it does not exist. */
    fun party(partyId: String): Flow<LiveEvent<Party?>>

    /** One transaction: picks a free code (silently retrying taken ones), writes the lock and the party. */
    suspend fun createParty(hostId: String, merchantId: String, nowMillis: Long): CreatePartyResult

    /** Appends [userId] to the member list inside a transaction, so simultaneous joins both land. */
    suspend fun join(partyId: String, userId: String): JoinPartyResult

    /** `wrongCodeAttempts.{userId}` += 1. */
    suspend fun recordWrongAttempt(partyId: String, userId: String)

    /**
     * Writes `status: expired` if the party is still stored `open` and past
     * `expiresAt`, and deletes its lock if the lock still belongs to it.
     * A lost race (another phone already did it) or a failure is ignored.
     */
    suspend fun expire(partyId: String)

    /** Expires [hostId]'s parties left `open` past their expiry (for example the app was closed at the time). */
    suspend fun expireStaleHostedParties(hostId: String, nowMillis: Long)
}
