package com.splitparty.android.testing

import com.splitparty.android.domain.analytics.Analytics
import com.splitparty.android.domain.analytics.AnalyticsEvent
import com.splitparty.android.domain.model.DemoMerchant
import com.splitparty.android.domain.model.LiveEvent
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.model.PartyCancellation
import com.splitparty.android.domain.model.PartyMember
import com.splitparty.android.domain.model.PartyStatus
import com.splitparty.android.domain.model.Transaction
import com.splitparty.android.domain.model.User
import com.splitparty.android.domain.model.UserLookup
import com.splitparty.android.domain.repository.CancelPartyResult
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.CreatePartyResult
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoResetResult
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.repository.JoinPartyResult
import com.splitparty.android.domain.repository.LastPartyRepository
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.repository.PartyWriteResult
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository
import com.splitparty.android.domain.split.Tip
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SplitPartyRepository]: tests push listener events into the public flows. */
class FakeSplitPartyRepository(knownUsers: List<User> = TestData.users) : SplitPartyRepository {
    val accounts = knownUsers.associateBy { it.id }.toMutableMap()

    /** When set, every lookup returns this instead of searching [accounts]. */
    var lookupOverride: UserLookup? = null

    /** When set, lookups suspend until it completes (to observe the busy state). */
    var lookupGate: CompletableDeferred<Unit>? = null
    val lookups = mutableListOf<String>()

    val feed = MutableStateFlow<LiveEvent<List<Transaction>>>(LiveEvent.Pending)
    val userTransactions = MutableStateFlow<LiveEvent<List<Transaction>>>(LiveEvent.Pending)
    val user = MutableStateFlow<LiveEvent<User?>>(LiveEvent.Pending)
    val users = MutableStateFlow<LiveEvent<Map<String, User>>>(LiveEvent.Pending)

    override suspend fun findUser(normalizedUsername: String): UserLookup {
        lookups += normalizedUsername
        lookupGate?.await()
        return lookupOverride ?: accounts[normalizedUsername]?.let(UserLookup::Found) ?: UserLookup.NotFound
    }

    override fun feed(): Flow<LiveEvent<List<Transaction>>> = feed

    override fun transactionsOf(userId: String): Flow<LiveEvent<List<Transaction>>> = userTransactions

    override fun user(userId: String): Flow<LiveEvent<User?>> = user

    override fun users(): Flow<LiveEvent<Map<String, User>>> = users
}

class FakeSessionRepository(initial: String? = null) : SessionRepository {
    override val signedInUserId = MutableStateFlow(initial)

    override suspend fun signIn(userId: String) {
        signedInUserId.value = userId
    }

    override suspend fun signOut() {
        signedInUserId.value = null
    }
}

class FakeDemoSettingsRepository(initial: Boolean = true, customSharesOn: Boolean = false) : DemoSettingsRepository {
    override val atMerchant = MutableStateFlow(initial)

    override suspend fun setAtMerchant(on: Boolean) {
        atMerchant.value = on
    }

    override val customShares = MutableStateFlow(customSharesOn)

    override suspend fun setCustomShares(on: Boolean) {
        customShares.value = on
    }
}

/** In-memory [PartyRepository]: tests push snapshots into [open] / [parties] and inspect the recorded calls. */
class FakePartyRepository : PartyRepository {
    val open = MutableStateFlow<LiveEvent<List<Party>>>(LiveEvent.Pending)
    val parties = mutableMapOf<String, MutableStateFlow<LiveEvent<Party?>>>()
    var createResult: CreatePartyResult = CreatePartyResult.Created("new-party")
    var joinResult: JoinPartyResult = JoinPartyResult.Joined
    var createGate: CompletableDeferred<Unit>? = null
    val created = mutableListOf<String>()
    val joins = mutableListOf<Pair<String, String>>()
    val wrongAttempts = mutableListOf<Pair<String, String>>()
    val expired = mutableListOf<String>()
    val staleCleanups = mutableListOf<String>()

    fun partyFlow(id: String) = parties.getOrPut(id) { MutableStateFlow(LiveEvent.Pending) }

    override fun openParties(merchantId: String): Flow<LiveEvent<List<Party>>> = open

    override fun party(partyId: String): Flow<LiveEvent<Party?>> = partyFlow(partyId)

    override suspend fun createParty(hostId: String, merchantId: String, nowMillis: Long): CreatePartyResult {
        created += hostId
        createGate?.await()
        return createResult
    }

    override suspend fun join(partyId: String, userId: String): JoinPartyResult {
        joins += partyId to userId
        return joinResult
    }

    override suspend fun recordWrongAttempt(partyId: String, userId: String) {
        wrongAttempts += partyId to userId
    }

    var writeResult: PartyWriteResult = PartyWriteResult.Done
    var writeGate: CompletableDeferred<Unit>? = null
    val writes = mutableListOf<String>()

    /** Like the real writes: on Done the change reaches this party's listeners. */
    private suspend fun write(partyId: String, label: String, change: (Party) -> Party): PartyWriteResult {
        writes += label
        writeGate?.await()
        if (writeResult == PartyWriteResult.Done) {
            val flow = parties[partyId]
            val current = flow?.value as? LiveEvent.Data
            current?.value?.let { flow.value = LiveEvent.Data(change(it), fromCache = false) }
        }
        return writeResult
    }

    override suspend fun setBill(partyId: String, hostId: String, billCents: Long) =
        write(partyId, "bill:$billCents") { it.copy(billCents = billCents, tip = it.tip ?: Tip.ZERO) }

    override suspend fun setTip(partyId: String, hostId: String, tip: Tip) = write(partyId, "tip:$tip") { it.copy(tip = tip) }

    override suspend fun removeMember(partyId: String, hostId: String, memberId: String) =
        write(partyId, "remove:$memberId") { p -> p.copy(members = p.members.filterNot { it.userId == memberId }) }

    /** Like the real write: refused (nothing written) when the amounts would not fit the total. */
    override suspend fun setCustomShare(partyId: String, hostId: String, memberId: String, cents: Long?): PartyWriteResult {
        val party = (parties[partyId]?.value as? LiveEvent.Data)?.value
        if (party != null && party.customShareProblemWith(memberId, cents) != null) {
            writes += "custom:$memberId:$cents:refused"
            return PartyWriteResult.NotAllowed
        }
        return write(partyId, "custom:$memberId:$cents") { p ->
            p.copy(members = p.members.map { if (it.userId == memberId) it.copy(customShareCents = cents) else it })
        }
    }

    override suspend fun leave(partyId: String, userId: String) =
        write(partyId, "leave:$userId") { p -> p.copy(members = p.members.filterNot { it.userId == userId }) }

    /** Like the real settle: already paid is Done with nothing written; otherwise everyone is marked paid. */
    override suspend fun settle(partyId: String, hostId: String): PartyWriteResult {
        val already = (parties[partyId]?.value as? LiveEvent.Data)?.value?.status == PartyStatus.Paid
        if (already) {
            writes += "settle:noop"
            return PartyWriteResult.Done
        }
        return write(partyId, "settle") { p ->
            p.copy(status = PartyStatus.Paid, tip = p.tip ?: Tip.ZERO, members = p.members.map { it.copy(paid = true) })
        }
    }

    /** Null: like the real cancel (host, open party). Otherwise this result, with nothing written. */
    var cancelResult: CancelPartyResult? = null
    var cancelGate: CompletableDeferred<Unit>? = null

    /** Like the real cancel: host + open → cancelled (listeners see it), already cancelled → no event. */
    override suspend fun cancel(partyId: String, hostId: String): CancelPartyResult {
        writes += "cancel:$hostId"
        cancelGate?.await()
        cancelResult?.let { return it }
        val flow = parties[partyId] ?: return CancelPartyResult.NotAllowed
        val party = (flow.value as? LiveEvent.Data)?.value ?: return CancelPartyResult.NotAllowed
        return when {
            party.hostId != hostId -> CancelPartyResult.NotAllowed

            party.isCancelled -> CancelPartyResult.Cancelled(null)

            party.status != PartyStatus.Open -> CancelPartyResult.NotAllowed

            else -> {
                flow.value = LiveEvent.Data(party.copy(status = PartyStatus.Cancelled), fromCache = false)
                open.value = (open.value as? LiveEvent.Data)?.let { list ->
                    LiveEvent.Data(list.value.filterNot { it.id == partyId }, list.fromCache)
                } ?: open.value
                CancelPartyResult.Cancelled(PartyCancellation(partyId, party.members.size, 0L))
            }
        }
    }

    /** Like the real write: a stored-open party becomes expired, and its listeners see it. */
    override suspend fun expire(partyId: String) {
        expired += partyId
        val flow = parties[partyId] ?: return
        val current = flow.value
        if (current is LiveEvent.Data && current.value?.status == PartyStatus.Open) {
            flow.value = LiveEvent.Data(current.value?.copy(status = PartyStatus.Expired), fromCache = false)
        }
    }

    override suspend fun expireStaleHostedParties(hostId: String, nowMillis: Long) {
        staleCleanups += hostId
    }
}

/** [Analytics] that keeps every event. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()

    override fun record(event: AnalyticsEvent) {
        events += event
    }
}

/** [LastPartyRepository] in memory. */
class FakeLastPartyRepository(var partyId: String? = null) : LastPartyRepository {
    override suspend fun lastPartyId(): String? = partyId

    override suspend fun remember(partyId: String) {
        this.partyId = partyId
    }

    override suspend fun forget() {
        partyId = null
    }
}

class FakeConnectivityObserver(online: Boolean = true) : ConnectivityObserver {
    override val isOnline = MutableStateFlow(online)
}

object TestData {
    val maxi = User("maxi", "maxi", "Maxi", "avatar_maxi", 25_000, listOf("annie", "christophe"))
    val annie = User("annie", "annie", "Annie", "avatar_annie", 18_050, listOf("maxi", "christophe"))
    val christophe = User("christophe", "christophe", "Christophe", "avatar_christophe", 32_075, listOf("maxi", "annie"))
    val users = listOf(maxi, annie)
    val everyone = listOf(maxi, annie, christophe)

    val CODE = listOf("🍕", "🍺")

    /** An open party at Tony's created at [createdAt] (epoch millis), members in join order. */
    fun party(
        id: String = "p1",
        host: String = "annie",
        members: List<String> = listOf(host),
        createdAt: Long = 0L,
        status: PartyStatus = PartyStatus.Open,
        wrongAttempts: Map<String, Long> = emptyMap(),
        billCents: Long? = null,
        tip: Tip? = null
    ) = Party(
        id, host, DemoMerchant.ID, CODE, status, createdAt, createdAt + Party.TTL_MILLIS,
        members.mapIndexed { i, uid -> PartyMember(uid, createdAt + i, false) }, wrongAttempts, billCents, tip
    )

    fun tx(id: String, payer: String = "annie", payee: String = "maxi", cents: Long = 3_115, memo: String = "Tony's Split Party") =
        Transaction(id, payer, payee, cents, memo, null, null, createdAtMillis = 0L, likesCount = 0, commentsCount = 0)

    /** A server-confirmed snapshot, as Firestore delivers it when online. */
    fun <T> server(value: T) = LiveEvent.Data(value, fromCache = false)
}

/** [DemoResetRepository] that records calls and returns [result], optionally after [gate]. */
class FakeDemoResetRepository(var result: DemoResetResult = DemoResetResult.Done) : DemoResetRepository {
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0

    override suspend fun reset(): DemoResetResult {
        calls++
        gate?.await()
        return result
    }
}
