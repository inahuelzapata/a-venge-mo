package com.splitparty.android.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.splitparty.android.R
import com.splitparty.android.data.NetworkMonitor
import com.splitparty.android.data.SplitPartyRepository
import com.splitparty.android.ui.home.HomeRoute
import com.splitparty.android.ui.me.MeRoute
import com.splitparty.android.ui.pay.PayRequestScreen
import com.splitparty.android.ui.theme.Brand

private object Routes {
    const val HOME = "home"
    const val PAY = "pay"
    const val ME = "me"
}

private val BarHeight = 68.dp

/**
 * Signed-in shell: Home, Pay/Request and Me behind a floating bottom bar
 * (docs/design/home-feed-reference-*.jpg). Home is the start destination and
 * every tab switch pops back to it, so system back from another tab returns to
 * Home and back from Home leaves the app.
 */
@Composable
fun MainShell(userId: String, repository: SplitPartyRepository, network: NetworkMonitor, onLogOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route ?: Routes.HOME
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Room for the floating bar so the last row can scroll above it.
    val contentPadding = PaddingValues(bottom = BarHeight + 24.dp + navBottom)

    Box(Modifier.fillMaxSize().background(Brand.Page)) {
        NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
            composable(Routes.HOME) { HomeRoute(userId, repository, network, contentPadding) }
            composable(Routes.PAY) { PayRequestScreen(contentPadding) }
            composable(Routes.ME) { MeRoute(userId, repository, network, contentPadding, onLogOut) }
        }
        BottomBar(
            current = current,
            onSelect = { route -> navController.switchTab(route) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun NavHostController.switchTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(BarHeight),
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, Brand.Outline),
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabItem("Home", R.drawable.ic_home_filled, R.drawable.ic_home_outlined, current == Routes.HOME, Modifier.weight(1f)) {
                    onSelect(Routes.HOME)
                }
                TabItem("Me", R.drawable.ic_person_filled, R.drawable.ic_person_outlined, current == Routes.ME, Modifier.weight(1f)) {
                    onSelect(Routes.ME)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        PayRequestButton(selected = current == Routes.PAY) { onSelect(Routes.PAY) }
    }
}

@Composable
private fun TabItem(
    label: String,
    @DrawableRes selectedIcon: Int,
    @DrawableRes icon: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) Brand.Chip else Color.Transparent, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(if (selected) selectedIcon else icon), contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, color = Brand.Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** The brand-blue round button on the right of the reference bar, labelled Pay/Request. */
@Composable
private fun PayRequestButton(selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(BarHeight).semantics { this.selected = selected },
        shape = RoundedCornerShape(50),
        color = if (selected) Brand.BlueDark else Brand.Blue,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp)
            Text("Pay/Request", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
