package com.splitparty.android.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.splitparty.android.R
import com.splitparty.android.ui.home.HomeRoute
import com.splitparty.android.ui.me.MeRoute
import com.splitparty.android.ui.pay.PayRequestScreen
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.Dimens
import com.splitparty.android.ui.theme.Type
import com.splitparty.android.ui.theme.bottomInset

/**
 * Signed-in shell: Home, Pay/Request and Me behind the floating tab bar of
 * docs/design/ios-captures/home-feed.png. Home is the start destination and
 * every tab switch pops back to it, so system back from another tab returns to
 * Home and back from Home leaves the app.
 */
@Composable
fun MainShell(userId: String, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    ShellFrame(
        current = backStackEntry?.destination.toTab(),
        onSelect = navController::switchTab,
        modifier = modifier
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeDestination,
            modifier = Modifier.fillMaxSize(),
            // iOS swaps tabs instantly; no cross-fade.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable<HomeDestination> { HomeRoute(userId, contentPadding) }
            composable<PayDestination> { PayRequestScreen(contentPadding) }
            composable<MeDestination> { MeRoute(userId, contentPadding) }
        }
    }
}

/**
 * The page with the floating bar on top. [content] gets the bottom padding that
 * keeps it clear of the bar; lists scroll underneath it. Stateless, so
 * screenshot tests render whole screens with it.
 */
@Composable
fun ShellFrame(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit) {
    val contentPadding = PaddingValues(bottom = bottomInset() + Dimens.TabBarInset)
    Box(modifier.fillMaxSize().background(Brand.Page)) {
        content(contentPadding)
        BottomBar(current = current, onSelect = onSelect, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** The bottom bar's tabs, in bar order, and the destination each one opens. */
enum class Tab(val destination: Any, val label: String) {
    Home(HomeDestination, "Home"),
    Pay(PayDestination, "Pay/Request"),
    Me(MeDestination, "Me")
}

private fun NavDestination?.toTab(): Tab = when {
    this?.hasRoute<PayDestination>() == true -> Tab.Pay
    this?.hasRoute<MeDestination>() == true -> Tab.Me
    else -> Tab.Home
}

/**
 * Every switch pops back to Home (the start destination), saving and restoring
 * each tab's state, so back from another tab returns to Home.
 */
private fun NavHostController.switchTab(tab: Tab) {
    if (currentDestination.toTab() == tab) return
    navigate(tab.destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

object ShellTestTags {
    fun tab(tab: Tab) = "tab_${tab.name.lowercase()}"
}

/**
 * Floating capsule, 92 tall, 16 from the sides and 4 above the gesture area:
 * three equal items with the icon above the label. The centre slot is the
 * 52 brand-blue circle; its "$" is a neutral placeholder until the brand mark exists.
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(
                start = Dimens.TabBarSideMargin,
                end = Dimens.TabBarSideMargin,
                bottom = bottomInset() + Dimens.TabBarBottomMargin
            )
            .fillMaxWidth()
            .height(Dimens.TabBarHeight)
            .dropShadow(
                CircleShape,
                Shadow(radius = 16.dp, offset = DpOffset(0.dp, 4.dp), color = Brand.Shadow.copy(alpha = 0.12f))
            )
            .background(Brand.TabBar, CircleShape)
            .border(1.dp, Brand.TabBarOutline, CircleShape)
            .padding(Dimens.TabBarPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Tab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = current == tab,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).testTag(ShellTestTags.tab(tab))
            )
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (selected) Brand.Ink else Brand.InkSecondary
    Column(
        modifier = modifier
            .height(Dimens.TabItemHeight)
            .background(if (selected && tab != Tab.Pay) Brand.TabSelected else Color.Transparent, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.height(Dimens.TabIconSlot), contentAlignment = Alignment.Center) {
            when (tab) {
                Tab.Home -> TabIcon(R.drawable.ic_house_fill, tint, width = 31.dp, height = 31.dp)

                Tab.Me -> TabIcon(R.drawable.ic_person_fill, tint, width = 27.dp, height = 26.dp)

                Tab.Pay -> Box(
                    Modifier.size(Dimens.TabIconSlot).background(Brand.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$", style = Type.DollarGlyph, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(tab.label, style = if (selected) Type.TabLabelSelected else Type.TabLabel, color = tint)
    }
}

@Composable
private fun TabIcon(@DrawableRes icon: Int, tint: Color, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(width, height))
}
