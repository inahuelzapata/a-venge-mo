package com.splitparty.android.e2e

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.splitparty.android.data.FirestorePartyRepository
import com.splitparty.android.data.firebase.FirebaseConnection
import com.splitparty.android.data.firebase.FirebaseSetup
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.domain.party.JoinCodes
import com.splitparty.android.domain.repository.CreatePartyResult
import com.splitparty.android.domain.repository.JoinPartyResult
import com.splitparty.android.domain.repository.PartyWriteResult
import com.splitparty.android.domain.split.Tip
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * The host's Pay ([FirestorePartyRepository.settle]) against the Firestore
 * emulator running backend/firestore.rules: every write here goes through the
 * real rules, exactly as on a phone. Seeding and clearing use the emulator's
 * admin REST endpoint (`Bearer owner`), which bypasses the rules, because
 * phones may not create users or merchants.
 *
 * Skipped unless an emulator is given. Run it with
 * `android/scripts/settle-rules-e2e.sh`, or by hand:
 * 1. `cd backend && npx firebase emulators:start --only firestore --project demo-split-party-settle`
 * 2. `./gradlew connectedDebugAndroidTest
 *      -Pandroid.testInstrumentationRunnerArguments.class=com.splitparty.android.e2e.SettleRulesE2ETest
 *      -Pandroid.testInstrumentationRunnerArguments.settleEmulator=10.0.2.2:8080`
 */
@RunWith(AndroidJUnit4::class)
class SettleRulesE2ETest {
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(
        if (Build.VERSION.SDK_INT >= ANDROID_17) GrantPermissionRule.grant(ACCESS_LOCAL_NETWORK) else GrantPermissionRule.grant()
    )

    private val args = InstrumentationRegistry.getArguments()
    private val host: String? = args.getString("settleEmulator")
    private val projectId: String = args.getString("settleProject") ?: "demo-split-party-settle"

    private lateinit var db: FirebaseFirestore
    private lateinit var admin: EmulatorAdmin
    private var now = System.currentTimeMillis()
    private lateinit var repo: FirestorePartyRepository

    @Before
    fun setUp() {
        assumeTrue("No settleEmulator argument: the rules e2e test runs only against the Firestore emulator", host != null)
        db = emulatorDb(projectId, host!!)
        admin = EmulatorAdmin(host, projectId)
        admin.clear()
        // The seeded world (backend/lib/seedData.js).
        admin.put("users/maxi", user("maxi", "Maxi", 25_000))
        admin.put("users/annie", user("annie", "Annie", 18_050))
        admin.put("users/christophe", user("christophe", "Christophe", 32_075))
        admin.put("merchants/tonys-greenwich", mapOf("name" to "Tony's", "city" to "Greenwich, CT"))
        repo = FirestorePartyRepository(db) { now }
    }

    @Test
    fun hostFlowThenPaySettlesEveryoneThroughTheRules() = runBlocking {
        val partyId = (repo.createParty("annie", MERCHANT, now) as CreatePartyResult.Created).partyId
        assertEquals(JoinPartyResult.Joined, repo.join(partyId, "maxi"))
        assertEquals(JoinPartyResult.Joined, repo.join(partyId, "christophe"))
        assertEquals(PartyWriteResult.Done, repo.setBill(partyId, "annie", 10_000))
        assertEquals(PartyWriteResult.Done, repo.setTip(partyId, "annie", Tip.Percent(18)))
        val code = (doc("parties/$partyId").get("joinCode") as List<*>).filterIsInstance<String>()

        assertEquals(PartyWriteResult.Done, repo.settle(partyId, "annie"))

        val party = doc("parties/$partyId")
        assertEquals("paid", party.getString("status"))
        assertNotNull(party.getTimestamp("paidAt"))
        assertEquals(10_000L, party.getLong("billCents"))
        assertEquals(mapOf("type" to "percent", "percent" to 18L), party.get("tip"))
        listOf("annie", "maxi", "christophe").forEach { assertEquals(true, party.getBoolean("members.$it.paid")) }
        assertFalse("join-code lock freed", doc("joinCodes/${JoinCodes.lockId(MERCHANT, code)}").exists())

        listOf("maxi", "christophe").forEach { payer ->
            val tx = doc("transactions/${partyId}_$payer")
            assertEquals(payer, tx.getString("payerId"))
            assertEquals("annie", tx.getString("payeeId"))
            assertEquals(listOf(payer, "annie"), tx.get("participantIds"))
            assertEquals(3_933L, tx.getLong("amountCents"))
            assertEquals("Tony's Split Party", tx.getString("memo"))
            assertEquals(MERCHANT, tx.getString("merchantId"))
            assertEquals("Tony's", tx.getString("merchantName"))
            assertEquals(partyId, tx.getString("partyId"))
            assertNotNull(tx.getTimestamp("createdAt"))
            assertEquals(0L, tx.getLong("likesCount"))
            assertEquals(0L, tx.getLong("commentsCount"))
            assertEquals("party", tx.getString("source"))
        }
        assertEquals(2, partyTransactions(partyId))
        assertBalances(annie = 18_050 + 7_866, maxi = 25_000 - 3_933, christophe = 32_075 - 3_933)
    }

    @Test
    fun aSecondPayAndTwoConcurrentPaysMoveEverythingOnce() = runBlocking {
        val partyId = seedParty(members = listOf("annie", "maxi", "christophe"), billCents = 10_000, tip = PERCENT_18)
        val results = listOf(async { repo.settle(partyId, "annie") }, async { repo.settle(partyId, "annie") }).awaitAll()
        assertEquals(listOf(PartyWriteResult.Done, PartyWriteResult.Done), results)
        assertEquals(PartyWriteResult.Done, repo.settle(partyId, "annie"))
        assertEquals(2, partyTransactions(partyId))
        assertBalances(annie = 18_050 + 7_866, maxi = 25_000 - 3_933, christophe = 32_075 - 3_933)
    }

    @Test
    fun noTipChosenSettlesWithAZeroCustomTip() = runBlocking {
        val partyId = seedParty(members = listOf("annie", "maxi", "christophe"), billCents = 10_000, tip = null)
        assertEquals(PartyWriteResult.Done, repo.settle(partyId, "annie"))
        val party = doc("parties/$partyId")
        assertEquals("paid", party.getString("status"))
        assertEquals(mapOf("type" to "custom", "amountCents" to 0L), party.get("tip"))
        // 100.00 for three: host 33.34, the others 33.33 each; only the two joinees are transacted.
        assertEquals(3_333L, doc("transactions/${partyId}_maxi").getLong("amountCents"))
        assertEquals(3_333L, doc("transactions/${partyId}_christophe").getLong("amountCents"))
        assertFalse(doc("transactions/${partyId}_annie").exists())
        assertBalances(annie = 18_050 + 6_666, maxi = 25_000 - 3_333, christophe = 32_075 - 3_333)
    }

    @Test
    fun aRemovedMemberIsNotSettled() = runBlocking {
        val partyId = seedParty(members = listOf("annie", "maxi"), billCents = 10_000, tip = PERCENT_18)
        assertEquals(PartyWriteResult.Done, repo.settle(partyId, "annie"))
        assertEquals(5_900L, doc("transactions/${partyId}_maxi").getLong("amountCents"))
        assertEquals(1, partyTransactions(partyId))
        assertBalances(annie = 18_050 + 5_900, maxi = 25_000 - 5_900, christophe = 32_075)
    }

    @Test
    fun aBalanceMayGoNegative() = runBlocking {
        admin.put("users/maxi", user("maxi", "Maxi", 1_000))
        val partyId = seedParty(members = listOf("annie", "maxi"), billCents = 10_000, tip = PERCENT_18)
        assertEquals(PartyWriteResult.Done, repo.settle(partyId, "annie"))
        assertEquals(1_000L - 5_900, doc("users/maxi").getLong("balanceCents"))
    }

    @Test
    fun anExpiredPartyIsNotSettledAndNothingIsWritten() = runBlocking {
        val partyId = seedParty(members = listOf("annie", "maxi"), billCents = 10_000, tip = PERCENT_18, createdAt = now - SIX_MINUTES)
        assertEquals(PartyWriteResult.NotAllowed, repo.settle(partyId, "annie"))
        assertEquals("open", doc("parties/$partyId").getString("status"))
        assertEquals(0, partyTransactions(partyId))
        assertBalances(annie = 18_050, maxi = 25_000, christophe = 32_075)
    }

    @Test
    fun aRulesRejectionLeavesThePartyOpenAndNoBalanceMoved() = runBlocking {
        // A stored party the rules no longer accept on update (wrongCodeAttempts must be a map):
        // the party update in the Pay commit is denied, so the whole commit must be.
        val partyId = seedParty(members = listOf("annie", "maxi"), billCents = 10_000, tip = PERCENT_18, wrongCodeAttempts = "broken")
        assertEquals(PartyWriteResult.Failed, repo.settle(partyId, "annie"))
        assertEquals("open", doc("parties/$partyId").getString("status"))
        assertEquals(0, partyTransactions(partyId))
        assertBalances(annie = 18_050, maxi = 25_000, christophe = 32_075)
    }

    private suspend fun doc(path: String): DocumentSnapshot = db.document(path).get(Source.SERVER).await()

    private suspend fun partyTransactions(partyId: String): Int =
        db.collection("transactions").whereEqualTo("partyId", partyId).get(Source.SERVER).await().size()

    private suspend fun assertBalances(annie: Long, maxi: Long, christophe: Long) {
        assertEquals(
            mapOf("annie" to annie, "maxi" to maxi, "christophe" to christophe),
            listOf("annie", "maxi", "christophe").associateWith { doc("users/$it").getLong("balanceCents") }
        )
    }

    /** A party document as the apps write it, put directly (rules bypassed) to set up a state. */
    private fun seedParty(
        members: List<String>,
        billCents: Long?,
        tip: Map<String, Any>?,
        createdAt: Long = now - ONE_MINUTE,
        wrongCodeAttempts: Any = emptyMap<String, Any>()
    ): String {
        val id = "party-${System.nanoTime()}"
        val created = Date(createdAt)
        admin.put(
            "parties/$id",
            mapOf(
                "hostId" to members.first(),
                "merchantId" to MERCHANT,
                "joinCode" to listOf("🍕", "🍺", "🎉"),
                "status" to "open",
                "createdAt" to created,
                "expiresAt" to Date(createdAt + FIVE_MINUTES),
                "paidAt" to null,
                "billCents" to billCents,
                "tip" to tip,
                "memberIds" to members,
                "members" to members.associateWith { mapOf("joinedAt" to created, "paid" to false) },
                "wrongCodeAttempts" to wrongCodeAttempts
            )
        )
        return id
    }

    private fun user(id: String, name: String, balance: Long) = mapOf(
        "username" to id,
        "displayName" to name,
        "avatar" to "avatar_$id",
        "balanceCents" to balance,
        "friendIds" to listOf("maxi", "annie", "christophe").filter { it != id }
    )

    private companion object {
        const val MERCHANT = "tonys-greenwich"
        const val ANDROID_17 = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val ONE_MINUTE = 60_000L
        const val FIVE_MINUTES = 5 * ONE_MINUTE
        const val SIX_MINUTES = 6 * ONE_MINUTE
        val PERCENT_18 = mapOf("type" to "percent", "percent" to 18L)

        /** One per process: useEmulator() may only be called before the instance is first used. */
        private var shared: FirebaseFirestore? = null

        fun emulatorDb(projectId: String, host: String): FirebaseFirestore = shared ?: run {
            val target = FirestoreTarget.Emulator(projectId, host)
            val connection = FirebaseSetup.connect(InstrumentationRegistry.getInstrumentation().targetContext, target)
            (connection as FirebaseConnection.Ready).db.also { shared = it }
        }
    }
}

/**
 * The Firestore emulator's REST API with the `owner` token (rules bypassed).
 * Plain sockets, because the app's network policy forbids cleartext HTTP
 * (the SDK's own gRPC channel is unaffected).
 */
private class EmulatorAdmin(hostPort: String, private val projectId: String) {
    private val host = hostPort.substringBeforeLast(':')
    private val port = hostPort.substringAfterLast(':').toInt()

    fun clear() = send("DELETE", "/emulator/v1/projects/$projectId/databases/(default)/documents", null)

    fun put(path: String, fields: Map<String, Any?>) {
        val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val body = JSONObject().put("fields", JSONObject().apply { fields.forEach { (k, v) -> put(k, value(v)) } })
        send("PATCH", "/v1/projects/$projectId/databases/(default)/documents/$encoded", body.toString())
    }

    private fun value(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)

        is String -> JSONObject().put("stringValue", v)

        is Boolean -> JSONObject().put("booleanValue", v)

        is Int, is Long -> JSONObject().put("integerValue", v.toString())

        is Date -> JSONObject().put("timestampValue", rfc3339(v))

        is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map(::value))))

        is Map<*, *> -> JSONObject().put(
            "mapValue",
            JSONObject().put("fields", JSONObject().apply { v.forEach { (k, item) -> put(k as String, value(item)) } })
        )

        else -> error("Unsupported value $v")
    }

    private fun rfc3339(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)

    private fun send(method: String, path: String, body: String?) {
        val bytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val head = "$method $path HTTP/1.1\r\nHost: $host:$port\r\nAuthorization: Bearer owner\r\n" +
                "Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
            val status = BufferedReader(InputStreamReader(socket.getInputStream())).readLine().orEmpty()
            check(status.split(' ').getOrNull(1) == "200") { "$method $path failed: $status" }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
