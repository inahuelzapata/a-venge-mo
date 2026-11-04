package com.splitparty.android.ui.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.splitparty.android.data.LiveEvent
import com.splitparty.android.data.NetworkMonitor
import com.splitparty.android.data.SplitPartyRepository
import com.splitparty.android.data.Transaction
import com.splitparty.android.data.User
import com.splitparty.android.data.Username
import com.splitparty.android.format.Money
import com.splitparty.android.ui.LiveState
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.toLiveState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MeViewModel(userId: String, repository: SplitPartyRepository, network: NetworkMonitor) : ViewModel() {
    /** `users/{userId}`, live: the balance updates as soon as the document changes. */
    val user: StateFlow<LiveState<User?>> = repository.user(userId)
        .toLiveState(network.isOnline)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveState.Loading)

    /** The schema's Me query: this user as payer or payee, newest first. */
    val transactions: StateFlow<LiveState<List<Transaction>>> = repository.transactionsOf(userId)
        .toLiveState(network.isOnline)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveState.Loading)

    val users: StateFlow<Map<String, User>> = repository.users()
        .filterIsInstance<LiveEvent.Data<Map<String, User>>>()
        .map { it.value }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@Composable
fun MeRoute(
    userId: String,
    repository: SplitPartyRepository,
    network: NetworkMonitor,
    contentPadding: PaddingValues,
    onLogOut: () -> Unit,
) {
    val viewModel: MeViewModel = viewModel(factory = viewModelFactory {
        initializer { MeViewModel(userId, repository, network) }
    })
    val user by viewModel.user.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    MeScreen(userId, user, transactions, users, contentPadding, onLogOut)
}

/**
 * Profile, mock balance and the user's own payments. Deliberately nothing
 * else in this slice (no friends list).
 */
@Composable
fun MeScreen(
    userId: String,
    user: LiveState<User?>,
    transactions: LiveState<List<Transaction>>,
    users: Map<String, User>,
    contentPadding: PaddingValues,
    onLogOut: () -> Unit,
) {
    val now = rememberNow()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val profile = (user as? LiveState.Ready)?.value
    val name = profile?.displayName ?: users[userId]?.displayName ?: Username.fallbackName(userId)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = top + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "profile") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(profile?.avatar ?: users[userId]?.avatar, name, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                    Text("@$userId", color = Brand.InkSecondary, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = onLogOut,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Brand.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Brand.Card, contentColor = Brand.Ink),
                ) { Text("Log out") }
            }
        }
        item(key = "balance") {
            when (user) {
                is LiveState.Unreachable -> UnreachableCard(user.message)
                is LiveState.Ready ->
                    if (user.value == null) {
                        MessageCard("🤔", "Account not found", "users/$userId no longer exists. Log out and sign in again.")
                    } else {
                        BalanceCard(user.value.balanceCents)
                    }
                LiveState.Loading -> BalanceCard(null)
            }
        }
        item(key = "activity-title") {
            Text(
                "Your activity",
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = Brand.Ink,
            )
        }
        when (transactions) {
            LiveState.Loading -> item(key = "loading") { LoadingCard() }
            is LiveState.Unreachable -> item(key = "error") { UnreachableCard(transactions.message) }
            is LiveState.Ready ->
                if (transactions.value.isEmpty()) {
                    item(key = "empty") { MessageCard("💸", "No payments yet", "Payments you send or receive show up here.") }
                } else {
                    items(transactions.value, key = { it.id }) { transaction ->
                        FeedRow(transaction, users, now, highlighted = false, modifier = Modifier.animateItem())
                    }
                }
        }
    }
}

@Composable
private fun BalanceCard(balanceCents: Long?) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Brand.Card, RoundedCornerShape(28.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Balance", color = Brand.InkSecondary, fontSize = 15.sp)
        Text(
            balanceCents?.let(Money::format) ?: "—",
            color = Brand.Ink,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("Mock wallet for the demo", color = Brand.InkSecondary, fontSize = 13.sp)
    }
}
