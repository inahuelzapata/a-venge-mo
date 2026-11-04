package com.splitparty.android.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitparty.android.di.domainModule
import com.splitparty.android.di.viewModelModule
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository
import com.splitparty.android.testing.FakeConnectivityObserver
import com.splitparty.android.testing.FakeSessionRepository
import com.splitparty.android.testing.FakeSplitPartyRepository
import com.splitparty.android.testing.TestData
import com.splitparty.android.testing.TestData.server
import com.splitparty.android.testing.TestData.tx
import com.splitparty.android.ui.me.MeTestTags
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.junit.After
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

    private val repository = FakeSplitPartyRepository().apply {
        users.value = server(TestData.users.associateBy { it.id })
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
        }
        startKoin { modules(fakeData, domainModule, viewModelModule) }
        compose.setContent { SplitPartyTheme { MainShell(userId = "maxi") } }
    }

    @After
    fun stopDi() = stopKoin()

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun startsOnHomeWithTheFeed() {
        compose.onNodeWithText("Feed row", substring = true).assertIsDisplayed()
    }

    @Test
    fun meTabShowsProfileAndBackReturnsHome() {
        compose.onNodeWithTag(ShellTestTags.tab(Tab.Me)).performClick()
        compose.onNodeWithText("@maxi").assertIsDisplayed()
        compose.onNodeWithText("$250.00").assertIsDisplayed()
        // The profile card fills the small test screen; the user's rows are below it.
        compose.onNodeWithTag(MeTestTags.LIST).performScrollToNode(hasText("My row", substring = true))
        compose.onNodeWithText("My row", substring = true).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Feed row", substring = true).assertIsDisplayed()
    }

    @Test
    fun payTabShowsThePlaceholderAndBackReturnsHome() {
        compose.onNodeWithTag(ShellTestTags.tab(Tab.Pay)).performClick()
        compose.onNodeWithText("Pay or request").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Feed row", substring = true).assertIsDisplayed()
    }

    /** TalkBack reads the content description, so the image-only button is announced by name. */
    @Test
    fun centreButtonIsAnnouncedAsPayOrRequestAndOpensThePlaceholder() {
        compose.onNodeWithContentDescription("Pay or request").performClick()
        compose.onNodeWithText("Pay or request").assertIsDisplayed()
    }
}
