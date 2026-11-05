package com.splitparty.android.domain.repository

import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.split.Tip
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

/** Outcome of a bill, tip, removal or leave write. Anything but [Done] has written nothing. */
sealed interface PartyWriteResult {
    data object Done : PartyWriteResult

    /** Expired or settled by the time the write ran, or the caller may not make this change. */
    data object NotAllowed : PartyWriteResult

    /** No connection or the write was rejected (rules). */
    data object Failed : PartyWriteResult
}

/** Split Party reads and writes (docs/firestore-schema.md, `parties` and `joinCodes`). */
@Suppress("TooManyFunctions") // the party contract: one function per documented read or write
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

    /**
     * Host only: `billCents` = [billCents]. While no tip is stored the same
     * update writes the 0% tip, so no phone ever sees a bill without a tip.
     */
    suspend fun setBill(partyId: String, hostId: String, billCents: Long): PartyWriteResult

    /** Host only, once a bill exists: `tip` = [tip]. */
    suspend fun setTip(partyId: String, hostId: String, tip: Tip): PartyWriteResult

    /** Host only: drops [memberId] (never the host) from `memberIds` and `members` in one write. */
    suspend fun removeMember(partyId: String, hostId: String, memberId: String): PartyWriteResult

    /** A non-host member leaves: drops [userId] from `memberIds` and `members`; wrong attempts untouched. */
    suspend fun leave(partyId: String, userId: String): PartyWriteResult

    /**
     * Host only, the Pay tap (docs/firestore-schema.md, "Paying a party"): one
     * transaction that creates `transactions/{partyId}_{member}` for every
     * joinee's share, moves the balances, marks everyone paid, sets `paid` and
     * frees the join-code lock. A party that is already `paid` returns [PartyWriteResult.Done]
     * and writes nothing, so a retried or doubled Pay never charges twice.
     * Not active, no bill or no joinee: [PartyWriteResult.NotAllowed].
     */
    suspend fun settle(partyId: String, hostId: String): PartyWriteResult

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
