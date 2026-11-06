package com.splitparty.android.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.splitparty.android.data.firebase.FirestoreSchema
import com.splitparty.android.data.firebase.toFirestore
import com.splitparty.android.data.firebase.toParty
import com.splitparty.android.domain.model.DemoMerchant
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyCancellation
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.party.JoinCodes
import com.splitparty.android.domain.party.PartySettlement
import com.splitparty.android.domain.repository.CancelPartyResult
import com.splitparty.android.domain.repository.CreatePartyResult
import com.splitparty.android.domain.repository.JoinPartyResult
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.repository.PartyWriteResult
import com.splitparty.android.domain.split.Tip
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
@Suppress("TooManyFunctions") // one function per write in docs/firestore-schema.md's party contract
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

    override suspend fun setBill(partyId: String, hostId: String, billCents: Long): PartyWriteResult = hostWrite(partyId, hostId) { party ->
        if (party.tip == null) {
            mapOf(FieldPath.of("billCents") to billCents, FieldPath.of("tip") to Tip.ZERO.toFirestore())
        } else {
            mapOf(FieldPath.of("billCents") to billCents)
        }
    }

    override suspend fun setTip(partyId: String, hostId: String, tip: Tip): PartyWriteResult = hostWrite(partyId, hostId) { party ->
        if (party.billCents == null) null else mapOf(FieldPath.of("tip") to tip.toFirestore())
    }

    override suspend fun removeMember(partyId: String, hostId: String, memberId: String): PartyWriteResult =
        hostWrite(partyId, hostId) { party ->
            if (memberId == party.hostId) null else memberRemoval(party, memberId)
        }

    override suspend fun leave(partyId: String, userId: String): PartyWriteResult = partyWrite(partyId) { party ->
        if (userId == party.hostId) null else memberRemoval(party, userId)
    }

    /** Both member fields in one update, so the rules' memberIds == members keys always holds. */
    private fun memberRemoval(party: Party, userId: String): Map<FieldPath, Any>? = if (!party.hasMember(userId)) {
        emptyMap()
    } else {
        mapOf(
            FieldPath.of("memberIds") to party.memberIds - userId,
            FieldPath.of("members", userId) to FieldValue.delete()
        )
    }

    private suspend fun hostWrite(partyId: String, hostId: String, fields: (Party) -> Map<FieldPath, Any>?): PartyWriteResult =
        partyWrite(partyId) { party -> if (party.hostId != hostId) null else fields(party) }

    /**
     * One transaction: re-reads the party, refuses (writes nothing) unless it is
     * active by the clock and [fields] returns the change, then updates only
     * those fields. A join racing this write is retried by the SDK on top of
     * it (or vice versa), so neither is lost. Null from [fields] = not allowed;
     * empty = already in that state.
     */
    private suspend fun partyWrite(partyId: String, fields: (Party) -> Map<FieldPath, Any>?): PartyWriteResult = attempt {
        val ref = parties.document(partyId)
        db.runTransaction { tx ->
            val party = tx.get(ref).toParty()
            val change = party?.takeIf { it.isActiveAt(clock()) }?.let(fields)
            when {
                change == null -> PartyWriteResult.NotAllowed

                change.isEmpty() -> PartyWriteResult.Done

                else -> {
                    val (first, rest) = change.entries.first() to change.entries.drop(1)
                    tx.update(ref, first.key, first.value, *rest.flatMap { listOf(it.key, it.value) }.toTypedArray())
                    PartyWriteResult.Done
                }
            }
        }.await()
    } ?: PartyWriteResult.Failed

    override suspend fun settle(partyId: String, hostId: String): PartyWriteResult =
        runSettlement(partyId, hostId) ?: if (paidOnServer(partyId)) PartyWriteResult.Done else PartyWriteResult.Failed

    /**
     * A commit that lost to another Pay of the same party is rejected by the
     * rules (`paid` is terminal) with PERMISSION_DENIED, which the SDK does not
     * retry. So after any failure, a party the server already shows paid means
     * the settlement this Pay wanted happened: report it done.
     */
    private suspend fun paidOnServer(partyId: String): Boolean =
        attempt { parties.document(partyId).get(Source.SERVER).await().toParty()?.status == PartyStatus.Paid } == true

    private suspend fun runSettlement(partyId: String, hostId: String): PartyWriteResult? = attempt {
        val partyRef = parties.document(partyId)
        db.runTransaction { tx ->
            // Reads first (a Firestore transaction rule), then every write in one commit.
            val party = tx.get(partyRef).toParty()
            when {
                party == null || party.hostId != hostId -> PartyWriteResult.NotAllowed

                // Already settled (a doubled tap, or a retry whose commit landed): nothing to do.
                party.status == PartyStatus.Paid -> PartyWriteResult.Done

                party.payBlockerAt(clock()) != null -> PartyWriteResult.NotAllowed

                else -> {
                    val merchantName = tx.get(db.collection(FirestoreSchema.MERCHANTS).document(party.merchantId))
                        .getString("name")?.takeIf { it.isNotBlank() } ?: DemoMerchant.NAME
                    val lockRef = locks.document(JoinCodes.lockId(party.merchantId, party.joinCode))
                    val ownLock = tx.get(lockRef).let { it.exists() && it.getString("partyId") == partyId }
                    writeSettlement(tx, party, merchantName)
                    if (ownLock) tx.delete(lockRef)
                    PartyWriteResult.Done
                }
            }
        }.await()
    }

    /** The shares, balances and party update of docs/firestore-schema.md "Paying a party". */
    private fun writeSettlement(tx: Transaction, party: Party, merchantName: String) {
        val shares = checkNotNull(party.split).shares
        val memo = PartySettlement.memo(merchantName)
        var collected = 0L
        party.joinees.forEach { member ->
            val share = shares[party.memberIds.indexOf(member.userId)]
            if (share <= 0L) return@forEach // the rules demand amountCents > 0; a $0 share moves nothing
            collected += share
            tx.set(
                db.collection(FirestoreSchema.TRANSACTIONS).document(PartySettlement.transactionId(party.id, member.userId)),
                mapOf(
                    "payerId" to member.userId,
                    "payeeId" to party.hostId,
                    "participantIds" to listOf(member.userId, party.hostId),
                    "amountCents" to share,
                    "memo" to memo,
                    "merchantId" to party.merchantId,
                    "merchantName" to merchantName,
                    "partyId" to party.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "likesCount" to 0L,
                    "commentsCount" to 0L,
                    "source" to "party"
                )
            )
            // No balance check: a balance may go negative (the PoC never blocks a payment).
            tx.update(db.collection(FirestoreSchema.USERS).document(member.userId), "balanceCents", FieldValue.increment(-share))
        }
        if (collected > 0L) {
            tx.update(db.collection(FirestoreSchema.USERS).document(party.hostId), "balanceCents", FieldValue.increment(collected))
        }
        val fields = buildMap<FieldPath, Any?> {
            put(FieldPath.of("status"), PartyStatus.Paid.wire)
            put(FieldPath.of("paidAt"), FieldValue.serverTimestamp())
            // The rules require a tip on a paid party and only accept the percent presets: 0% is custom 0.
            if (party.tip == null) put(FieldPath.of("tip"), Tip.ZERO.toFirestore())
            party.memberIds.forEach { put(FieldPath.of("members", it, "paid"), true) }
        }
        val (first, rest) = fields.entries.first() to fields.entries.drop(1)
        tx.update(parties.document(party.id), first.key, first.value, *rest.flatMap { listOf(it.key, it.value) }.toTypedArray())
    }

    override suspend fun cancel(partyId: String, hostId: String): CancelPartyResult =
        runCancel(partyId, hostId) ?: cancelledOnServer(partyId, hostId) ?: CancelPartyResult.Failed

    /**
     * After a failure (typically a lost commit acknowledgement): a party the server
     * shows cancelled by [hostId] means this cancel landed (only the host can cancel).
     */
    private suspend fun cancelledOnServer(partyId: String, hostId: String): CancelPartyResult? =
        attempt { parties.document(partyId).get(Source.SERVER).await().toParty() }
            ?.takeIf { it.isCancelled && it.hostId == hostId }
            ?.let { CancelPartyResult.Cancelled(it.cancellationAt(it.cancelledAtMillis ?: clock())) }

    private fun Party.cancellationAt(nowMillis: Long) =
        PartyCancellation(id, members.size, ((nowMillis - createdAtMillis) / MILLIS_PER_SECOND).coerceAtLeast(0L))

    private suspend fun runCancel(partyId: String, hostId: String): CancelPartyResult? = attempt {
        val partyRef = parties.document(partyId)
        db.runTransaction { tx ->
            val party = tx.get(partyRef).toParty()
            val now = clock()
            when {
                party == null || party.hostId != hostId -> CancelPartyResult.NotAllowed

                // A retried tap whose first commit landed: nothing to write, nothing to log.
                party.isCancelled -> CancelPartyResult.Cancelled(null)

                !party.canCancel(hostId, now) -> CancelPartyResult.NotAllowed

                else -> {
                    val lockRef = locks.document(JoinCodes.lockId(party.merchantId, party.joinCode))
                    val ownLock = tx.get(lockRef).let { it.exists() && it.getString("partyId") == partyId }
                    tx.update(
                        partyRef,
                        "status",
                        PartyStatus.Cancelled.wire,
                        "cancelledAt",
                        FieldValue.serverTimestamp(),
                        "cancelledBy",
                        hostId
                    )
                    // Freed so the host can start again at once (never a newer party's lock).
                    if (ownLock) tx.delete(lockRef)
                    CancelPartyResult.Cancelled(party.cancellationAt(now))
                }
            }
        }.await()
    }

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
        const val MILLIS_PER_SECOND = 1_000L

        /** 144 codes per merchant; a handful of tries always finds a free one at demo scale. */
        const val MAX_CODE_TRIES = 20
    }
}
