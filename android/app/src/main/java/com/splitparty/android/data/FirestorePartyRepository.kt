package com.splitparty.android.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.splitparty.android.data.firebase.FirestoreSchema
import com.splitparty.android.data.firebase.toParty
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.party.JoinCodes
import com.splitparty.android.domain.repository.CreatePartyResult
import com.splitparty.android.domain.repository.JoinPartyResult
import com.splitparty.android.domain.repository.PartyRepository
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PartyRepository] on Firestore, following docs/firestore-schema.md: party
 * creation is one transaction with the join-code lock, joins are transactions
 * (so two simultaneous joins both land: the loser is retried by the SDK on top
 * of the winner's write), wrong attempts use `FieldValue.increment`.
 *
 * Transactions need the server, so offline they fail instead of queueing: a
 * failed create leaves nothing behind.
 */
class FirestorePartyRepository(private val db: FirebaseFirestore, private val clock: () -> Long = System::currentTimeMillis) :
    PartyRepository {
    private val parties get() = db.collection(FirestoreSchema.PARTIES)
    private val locks get() = db.collection(FirestoreSchema.JOIN_CODES)

    /** `parties where merchantId == m where status == open where expiresAt > now` (declared composite index). */
    override fun openParties(merchantId: String): Flow<LiveEvent<List<Party>>> = listenQuery({
        parties.whereEqualTo("merchantId", merchantId)
            .whereEqualTo("status", PartyStatus.Open.wire)
            .whereGreaterThan("expiresAt", Timestamp(Date(clock())))
    }) { snapshot -> snapshot.documents.mapNotNull { it.toParty() } }

    override fun party(partyId: String): Flow<LiveEvent<Party?>> = listenDocument(parties.document(partyId)) { it.toParty() }

    override suspend fun createParty(hostId: String, merchantId: String, nowMillis: Long): CreatePartyResult {
        val partyRef = parties.document()
        val createdAt = Timestamp(Date(nowMillis))
        val expiresAt = Timestamp(Date(nowMillis + Party.TTL_MILLIS))
        return attempt {
            db.runTransaction { tx ->
                // A taken code is retried here, inside the transaction, so the user never sees it.
                var code = JoinCodes.random()
                var tries = 1
                while (lockTaken(tx, merchantId, code, nowMillis)) {
                    check(tries < MAX_CODE_TRIES) { "No free join code" }
                    code = JoinCodes.random()
                    tries++
                }
                tx.set(
                    locks.document(JoinCodes.lockId(merchantId, code)),
                    mapOf("merchantId" to merchantId, "joinCode" to code, "partyId" to partyRef.id, "expiresAt" to expiresAt)
                )
                tx.set(
                    partyRef,
                    mapOf(
                        "hostId" to hostId,
                        "merchantId" to merchantId,
                        "joinCode" to code,
                        "status" to PartyStatus.Open.wire,
                        "createdAt" to createdAt,
                        "expiresAt" to expiresAt,
                        "paidAt" to null,
                        "billCents" to null,
                        "tip" to null,
                        "memberIds" to listOf(hostId),
                        "members" to mapOf(hostId to mapOf("joinedAt" to createdAt, "paid" to false)),
                        "wrongCodeAttempts" to emptyMap<String, Long>()
                    )
                )
            }.await()
            CreatePartyResult.Created(partyRef.id)
        } ?: CreatePartyResult.Failed
    }

    /** Taken = a lock exists and its `expiresAt` is still ahead; a stale lock is simply overwritten. */
    private fun lockTaken(tx: Transaction, merchantId: String, code: List<String>, nowMillis: Long): Boolean {
        val lock = tx.get(locks.document(JoinCodes.lockId(merchantId, code)))
        val heldUntil = lock.getTimestamp("expiresAt")?.toDate()?.time
        return lock.exists() && heldUntil != null && heldUntil > nowMillis
    }

    override suspend fun join(partyId: String, userId: String): JoinPartyResult = attempt {
        db.runTransaction { tx ->
            val party = tx.get(parties.document(partyId)).toParty()
            when {
                party == null || !party.isActiveAt(clock()) || party.isLockedOut(userId) -> JoinPartyResult.NotJoinable

                party.hasMember(userId) -> JoinPartyResult.Joined

                party.members.size >= Party.MAX_MEMBERS -> JoinPartyResult.NotJoinable

                else -> {
                    tx.update(
                        parties.document(partyId),
                        FieldPath.of("memberIds"),
                        party.memberIds + userId,
                        FieldPath.of("members", userId),
                        mapOf("joinedAt" to Timestamp(Date(clock())), "paid" to false)
                    )
                    JoinPartyResult.Joined
                }
            }
        }.await()
    } ?: JoinPartyResult.Failed

    override suspend fun recordWrongAttempt(partyId: String, userId: String) {
        // Not awaited past the timeout: offline the increment is queued and lands on reconnect.
        attempt { parties.document(partyId).update(FieldPath.of("wrongCodeAttempts", userId), FieldValue.increment(1)).await() }
    }

    override suspend fun expire(partyId: String) {
        attempt {
            db.runTransaction { tx ->
                val party = tx.get(parties.document(partyId)).toParty() ?: return@runTransaction
                val lockRef = locks.document(JoinCodes.lockId(party.merchantId, party.joinCode))
                val lock = tx.get(lockRef)
                val pastExpiry = clock() >= party.expiresAtMillis
                if (party.status == PartyStatus.Open && pastExpiry) {
                    tx.update(parties.document(partyId), "status", PartyStatus.Expired.wire)
                }
                // Only our own lock: a newer party may already hold the same code.
                if (party.status != PartyStatus.Open || pastExpiry) {
                    if (lock.exists() && lock.getString("partyId") == partyId) tx.delete(lockRef)
                }
            }.await()
        }
    }

    override suspend fun expireStaleHostedParties(hostId: String, nowMillis: Long) {
        val stale = attempt {
            parties.whereEqualTo("hostId", hostId).whereEqualTo("status", PartyStatus.Open.wire).get(Source.SERVER).await()
                .documents.mapNotNull { it.toParty() }
                .filter { nowMillis >= it.expiresAtMillis }
        }.orEmpty()
        stale.forEach { expire(it.id) }
    }

    /** Null on a Firestore error (offline, rules) or when the server does not answer in time. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        withTimeoutOrNull(WRITE_TIMEOUT_MS) { block() }
    } catch (_: FirebaseFirestoreException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 8_000L

        /** 1728 codes per merchant; a handful of tries always finds a free one at demo scale. */
        const val MAX_CODE_TRIES = 20
    }
}
