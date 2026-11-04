package com.splitparty.android.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.splitparty.android.R
import com.splitparty.android.ui.components.BrandMark
import com.splitparty.android.ui.components.BrandMarkSizes
import com.splitparty.android.ui.home.HomeRoute
import com.splitparty.android.ui.me.MeRoute
import com.splitparty.android.ui.pay.PayRequestScreen
import com.splitparty.android.ui.theme.Brand

private val BarHeight = BrandMarkSizes.BottomBarHeight

/**
 * Signed-in shell: Home, Pay/Request and Me behind a floating bottom bar
 * (docs/design/home-feed-reference-*.jpg). Home is the start destination and
 * every tab switch pops back to it, so system back from another tab returns to
 * Home and back from Home leaves the app.
 */
@Composable
fun MainShell(userId: String, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination.toTab()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Room for the floating bar so the last row can scroll above it.
    val contentPadding = PaddingValues(bottom = BarHeight + 24.dp + navBottom)

    Box(modifier.fillMaxSize().background(Brand.Page)) {
        NavHost(navController = navController, startDestination = HomeDestination, modifier = Modifier.fillMaxSize()) {
            composable<HomeDestination> { HomeRoute(userId, contentPadding) }
            composable<PayDestination> { PayRequestScreen(contentPadding) }
            composable<MeDestination> { MeRoute(userId, contentPadding) }
        }
        BottomBar(
            current = current,
            onSelect = navController::switchTab,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** The bottom bar's tabs and the destination each one opens. */
enum class Tab(val destination: Any) {
    Home(HomeDestination),
    Pay(PayDestination),
    Me(MeDestination)
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
 * Floating capsule with Home and Me as regular tabs and, between them, the
 * Pay/Request action as a raised round button carrying the white Aven-mo mark
 * (Venmo-style), matching iOS. The box is [BrandMarkSizes.PayButtonLift] taller
 * than the capsule so the raised part of the button is inside it and still
 * receives taps (Compose drops touches outside a parent's bounds).
 */
@Composable
internal fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .padding(top = BrandMarkSizes.PayButtonLift)
                .fillMaxWidth()
                .height(BarHeight),
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, Brand.Outline),
            shadowElevation = 6.dp
        ) {
            Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabItem(
                    label = "Home",
                    selectedIcon = R.drawable.ic_home_filled,
                    icon = R.drawable.ic_home_outlined,
                    selected = current == Tab.Home,
                    onClick = { onSelect(Tab.Home) },
                    modifier = Modifier.weight(1f).testTag(ShellTestTags.tab(Tab.Home))
                )
                // Keeps Home and Me clear of the button.
                Spacer(Modifier.width(BrandMarkSizes.PayButtonDiameter + 8.dp))
                TabItem(
                    label = "Me",
                    selectedIcon = R.drawable.ic_person_filled,
                    icon = R.drawable.ic_person_outlined,
                    selected = current == Tab.Me,
                    onClick = { onSelect(Tab.Me) },
                    modifier = Modifier.weight(1f).testTag(ShellTestTags.tab(Tab.Me))
                )
            }
        }
        PayRequestButton(
            selected = current == Tab.Pay,
            onClick = { onSelect(Tab.Pay) },
            modifier = Modifier.align(Alignment.TopCenter).testTag(ShellTestTags.tab(Tab.Pay))
        )
    }
}

@Composable
private fun TabItem(
    label: String,
    @DrawableRes selectedIcon: Int,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) Brand.Chip else Color.Transparent, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(if (selected) selectedIcon else icon),
            contentDescription = null,
            tint = Brand.Ink,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Brand.Ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Accessibility label of the centre button, the same words iOS uses. */
const val PAY_BUTTON_DESCRIPTION = "Pay or request"

/**
 * Raised brand-blue circle with the white mark and no text. It looks the same
 * whether or not Pay/Request is open; only its selected semantics change.
 */
@Composable
private fun PayRequestButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(BrandMarkSizes.PayButtonDiameter)
            .semantics {
                contentDescription = PAY_BUTTON_DESCRIPTION
                this.selected = selected
            },
        shape = CircleShape,
        color = Brand.PayButtonBlue,
        shadowElevation = BrandMarkSizes.PayButtonElevation
    ) {
        Box(contentAlignment = Alignment.Center) {
            BrandMark(size = BrandMarkSizes.PayButtonMark, color = Color.White)
        }
    }
}
