package com.splitparty.android.e2e

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.splitparty.android.di.domainModule
import com.splitparty.android.di.viewModelModule
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.model.Party
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository
import com.splitparty.android.domain.split.Tip
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeDemoResetRepository
import com.splitparty.android.testing.FakeDemoSettingsRepository
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.ui.party.EntryTestTags
import com.splitparty.android.ui.scan.ReceiptRecognizer
import com.splitparty.android.ui.scan.ScanOutcome
import com.splitparty.android.ui.shell.MainShell
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.junit.rules.ExternalResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** A recognizer whose answer the test picks; it records every photo it was given (size only). */
class ScriptedRecognizer(var outcome: ScanOutcome = ScanOutcome.NoTotal) : ReceiptRecognizer {
    val photos = mutableListOf<Pair<Int, Int>>()

    override suspend fun warmUp() = Unit

    override suspend fun recognize(photo: Bitmap): ScanOutcome {
        photos += photo.width to photo.height
        return outcome
    }
}

/**
 * The real signed-in shell, ViewModels and use cases on the device, over fake
 * data (no Firebase): Koin is restarted with the fakes before each test. The
 * app's own Application starts Koin with the real modules first, so stop it.
 */
class PartyHarness(private val userId: String) : ExternalResource() {
    val parties = FakePartyRepository()
    val demoReset = FakeDemoResetRepository()
    val recognizer = ScriptedRecognizer()
    val data = FakeSplitPartyRepository().apply {
        users.value = server(TestData.everyone.associateBy { it.id })
        feed.value = server(emptyList())
        user.value = server(TestData.everyone.first { it.id == userId })
        userTransactions.value = server(emptyList())
    }

    override fun before() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<SplitPartyRepository> { data }
                    single<SessionRepository> { FakeSessionRepository(userId) }
                    single<ConnectivityObserver> { FakeConnectivityObserver() }
                    single<PartyRepository> { parties }
                    single<DemoSettingsRepository> { FakeDemoSettingsRepository() }
                    single<DemoResetRepository> { demoReset }
                    single<Clock> { Clock { System.currentTimeMillis() } }
                    single<ReceiptRecognizer> { recognizer }
                },
                domainModule,
                viewModelModule
            )
        }
    }

    override fun after() {
        stopKoin()
    }

    /** An open party at Tony's, created just now, that this user is in (the app opens it on launch). */
    fun party(
        host: String = "maxi",
        members: List<String> = listOf("maxi", "annie", "christophe"),
        billCents: Long? = null,
        tip: Tip? = null
    ): Party = TestData.party(
        id = PARTY_ID,
        host = host,
        members = members,
        createdAt = System.currentTimeMillis() - 1_000,
        billCents = billCents,
        tip = tip
    )

    /** Publishes [party] as the server would: the party document and the open-parties query. */
    fun publish(party: Party) {
        parties.partyFlow(PARTY_ID).value = server(party)
        parties.open.value = server(listOf(party))
    }

    companion object {
        const val PARTY_ID = "p1"
    }
}

typealias ShellRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** Shows the signed-in shell, as the app does after login. */
fun ShellRule.launchShell(userId: String) = setContent { SplitPartyTheme { MainShell(userId) } }

fun ShellRule.type(text: String) = text.forEach { onNodeWithTag(EntryTestTags.key(it.toString())).performClick() }

fun ShellRule.clearAmount(chars: Int = 10) = repeat(chars) { onNodeWithTag(EntryTestTags.key("⌫")).performClick() }

/** Waits (real time) until a node exists: Firestore-like updates arrive asynchronously. */
fun ShellRule.awaitTag(tag: String, timeoutMs: Long = 5_000): SemanticsNodeInteraction {
    waitUntil(timeoutMs) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    return onNodeWithTag(tag)
}

fun ShellRule.awaitDescription(description: String, timeoutMs: Long = 5_000): SemanticsNodeInteraction {
    waitUntil(timeoutMs) { onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
    return onNode(hasContentDescription(description))
}

fun ShellRule.tapTag(tag: String) = awaitTag(tag).performScrollTo().performClick()
