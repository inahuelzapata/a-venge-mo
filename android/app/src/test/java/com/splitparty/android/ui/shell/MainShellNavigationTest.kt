package com.splitparty.android.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.di.domainModule
import com.splitparty.android.di.viewModelModule
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.repository.PartyRepository
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeDemoResetRepository
import com.splitparty.android.testing.FakeDemoSettingsRepository
import com.splitparty.android.testing.FakePartyRepository
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.party
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.testing.TestData.tx
import com.splitparty.android.ui.home.HomeTestTags
import com.splitparty.android.ui.home.PartyBannerTestTags
import com.splitparty.android.ui.home.SplitPartyCardTestTags
import com.splitparty.android.ui.me.MeTestTags
import com.splitparty.android.ui.party.PartyTestTags
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** The real shell, ViewModels and use cases from Koin; only the data sources are fakes. */
@RunWith(AndroidJUnit4::class)
class MainShellNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val parties = FakePartyRepository().apply {
        // Nobody is in a party at launch (the relaunch check sees this first).
        open.value = server(emptyList())
    }

    private val repository = FakeSplitPartyRepository().apply {
        users.value = server(TestData.everyone.associateBy { it.id })
        feed.value = server(listOf(tx("a", memo = "Feed row")))
        user.value = server(TestData.maxi)
        userTransactions.value = server(listOf(tx("b", memo = "My row")))
    }

    @Before
    fun startDi() {
        val fakeData = module {
            single<SplitPartyRepository> { repository }
            single<SessionRepository> { FakeSessionRepository("maxi") }
            single<ConnectivityObserver> { FakeConnectivityObserver() }
            single<PartyRepository> { parties }
            single<DemoSettingsRepository> { FakeDemoSettingsRepository() }
            single<DemoResetRepository> { FakeDemoResetRepository() }
            single<Clock> { Clock { NOW } }
        }
        startKoin { modules(fakeData, domainModule, viewModelModule) }
    }

    /** Opens the shell signed in as Maxi; set the party snapshots before calling it to test a relaunch. */
    private fun launch() = compose.setContent { SplitPartyTheme { MainShell(userId = "maxi") } }

    @After
    fun stopDi() = stopKoin()

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun startsOnHomeWithTheFeed() {
        launch()
        compose.onNodeWithText("Feed row", substring = true).assertIsDisplayed()
    }

    @Test
    fun meTabShowsProfileAndBackReturnsHome() {
        launch()
        compose.onNodeWithTag(ShellTestTags.tab(Tab.Me)).performClick()
        compose.onNodeWithText("@maxi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Balance, 250 dollars").assertIsDisplayed()
        // The profile card fills the small test screen; the user's rows are below it.
        compose.onNodeWithTag(MeTestTags.LIST).performScrollToNode(hasText("My row", substring = true))
        compose.onNodeWithText("My row", substring = true).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Feed row", substring = true).assertIsDisplayed()
    }

    @Test
    fun payTabShowsThePlaceholderAndBackReturnsHome() {
        launch()
        compose.onNodeWithTag(ShellTestTags.tab(Tab.Pay)).performClick()
        compose.onNodeWithText("Pay or request").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Feed row", substring = true).assertIsDisplayed()
    }

    private val maxisParty = party(id = "new-party", host = "maxi")
    private val anniesParty = party(id = "annies", host = "annie")

    @Test
    fun startAPartyOpensThePartyAndSystemBackReturnsHomeWithBackToYourParty() {
        launch()
        parties.partyFlow("new-party").value = server(maxisParty)
        compose.onNodeWithText("Start a party").performClick()
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()
        compose.onNodeWithText("Expires in 4:50").assertIsDisplayed()

        parties.open.value = server(listOf(maxisParty))
        pressBack()
        compose.onNodeWithTag(PartyBannerTestTags.BACK_TO_PARTY).assertIsDisplayed()
        compose.onNodeWithText("Start a party").assertDoesNotExist()
        compose.onNodeWithText("Back to your party").performClick()
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()

        // On-screen back too; never further than Home (login is outside the shell).
        compose.onNodeWithTag(PartyTestTags.BACK).performClick()
        compose.onNodeWithTag(PartyBannerTestTags.BACK_TO_PARTY).assertIsDisplayed()
    }

    @Test
    fun bannerImageStartsAPartyToo() {
        launch()
        parties.partyFlow("new-party").value = server(maxisParty)
        compose.onNodeWithTag(SplitPartyCardTestTags.IMAGE).performClick()
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()
    }

    @Test
    fun joinBannerOpensConfirmationAndBackKeepsTheBanner() {
        launch()
        parties.partyFlow("annies").value = server(anniesParty)
        parties.open.value = server(listOf(anniesParty))
        compose.onNodeWithTag(PartyBannerTestTags.JOIN).performClick()
        compose.onNodeWithText("Join Annie's party").assertIsDisplayed()

        pressBack()
        compose.onNodeWithTag(PartyBannerTestTags.JOIN).assertIsDisplayed()
    }

    @Test
    fun correctCodeLandsOnThePartyAndBackGoesHomeNotToConfirmation() {
        launch()
        parties.partyFlow("annies").value = server(anniesParty)
        parties.open.value = server(listOf(anniesParty))
        compose.onNodeWithTag(PartyBannerTestTags.JOIN).performClick()
        compose.onNode(hasContentDescription("pizza, beer")).performScrollTo().performClick()
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()
        assertEquals(listOf("annies" to "maxi"), parties.joins)

        val joined = party(id = "annies", host = "annie", members = listOf("annie", "maxi"))
        parties.open.value = server(listOf(joined))
        pressBack()
        compose.onNodeWithTag(PartyBannerTestTags.BACK_TO_PARTY).assertIsDisplayed()
        compose.onNodeWithText("Join Annie's party").assertDoesNotExist()
    }

    @Test
    fun relaunchInAnOpenPartyOpensItStraightAway() {
        val joined = party(id = "annies", host = "annie", members = listOf("annie", "maxi"))
        parties.partyFlow("annies").value = server(joined)
        parties.open.value = server(listOf(joined))
        launch()
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()
        compose.onNodeWithContentDescription("Annie, host").assertExists()
        pressBack()
        compose.onNodeWithTag(PartyBannerTestTags.BACK_TO_PARTY).assertIsDisplayed()
    }

    @Test
    fun removedByTheHostReturnsHomeWithTheMessageAndTheJoinBanner() {
        val joined = party(id = "annies", host = "annie", members = listOf("annie", "maxi", "christophe"))
        parties.partyFlow("annies").value = server(joined)
        parties.open.value = server(listOf(joined))
        launch()
        compose.onNodeWithText("Split Party at Tony's").assertIsDisplayed()

        val without = party(id = "annies", host = "annie", members = listOf("annie", "christophe"))
        parties.open.value = server(listOf(without))
        parties.partyFlow("annies").value = server(without)
        compose.onNodeWithTag(ShellTestTags.NOTICE).assertIsDisplayed()
        compose.onNodeWithText("You were removed from Annie's party").assertIsDisplayed()
        compose.onNodeWithText("Split Party at Tony's").assertDoesNotExist()
        // Not locked out: the Join banner is back, exactly as for a first-time joiner.
        compose.onNodeWithTag(PartyBannerTestTags.JOIN).assertIsDisplayed()
    }

    @Test
    fun leavingReturnsHomeWithTheJoinBannerAndNoRemovedMessage() {
        val joined = party(id = "annies", host = "annie", members = listOf("annie", "maxi"))
        parties.partyFlow("annies").value = server(joined)
        parties.open.value = server(listOf(joined))
        launch()
        compose.onNodeWithTag(PartyTestTags.LEAVE).performScrollTo().performClick()
        compose.onNodeWithText("Leave Annie's party?").assertIsDisplayed()
        parties.open.value = server(listOf(party(id = "annies", host = "annie")))
        compose.onNodeWithTag(PartyTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf("leave:maxi"), parties.writes)
        compose.onNodeWithTag(PartyBannerTestTags.JOIN).assertIsDisplayed()
        compose.onNodeWithTag(ShellTestTags.NOTICE).assertDoesNotExist()
    }

    @Test
    fun homeTabFromThePartyReturnsHome() {
        launch()
        parties.partyFlow("new-party").value = server(maxisParty)
        compose.onNodeWithText("Start a party").performClick()
        compose.onNodeWithTag(ShellTestTags.tab(Tab.Home)).performClick()
        compose.onNodeWithText("Split Party at Tony's").assertDoesNotExist()
    }

    @Test
    fun backFromThePartyKeepsTheFeedScrollPosition() {
        launch()
        repository.feed.value = server((1..20).map { tx("r$it", memo = "Row $it") })
        parties.partyFlow("annies").value = server(anniesParty)
        parties.open.value = server(listOf(anniesParty))
        // Header scrolled off, the banner at the top: a position Home only keeps if its state survives.
        compose.onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(1)
        val bannerTop = compose.onNodeWithTag(PartyBannerTestTags.JOIN).getUnclippedBoundsInRoot().top

        compose.onNodeWithTag(PartyBannerTestTags.JOIN).performClick()
        compose.onNodeWithText("Join Annie's party").assertIsDisplayed()
        pressBack()

        assertEquals(bannerTop, compose.onNodeWithTag(PartyBannerTestTags.JOIN).getUnclippedBoundsInRoot().top)
    }

    /** TalkBack reads the content description, so the image-only button is announced by name. */
    @Test
    fun centreButtonIsAnnouncedAsPayOrRequestAndOpensThePlaceholder() {
        launch()
        compose.onNodeWithContentDescription("Pay or request").performClick()
        compose.onNodeWithText("Pay or request").assertIsDisplayed()
    }

    private companion object {
        /** Ten seconds into every test party (created at 0, expiring at 5:00). */
        const val NOW = 10_000L
    }
}
