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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitparty.android.domain.live.LiveState
import com.splitparty.android.format.Money
import com.splitparty.android.ui.components.Avatar
import com.splitparty.android.ui.components.FeedRow
import com.splitparty.android.ui.components.LoadingCard
import com.splitparty.android.ui.components.MessageCard
import com.splitparty.android.ui.components.PreviewData
import com.splitparty.android.ui.components.UnreachableCard
import com.splitparty.android.ui.components.rememberNow
import com.splitparty.android.ui.theme.Brand
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
@Composable
fun MeRoute(
    userId: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = koinViewModel { parametersOf(userId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MeScreen(state = state, contentPadding = contentPadding, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Profile, mock balance and the user's own payments. Deliberately nothing
 * else in this slice (no friends list).
 */
@Composable
fun MeScreen(state: MeUiState, contentPadding: PaddingValues, onAction: (MeAction) -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val name = state.displayName
    val userId = state.userId
    val user = state.profile
    val transactions = state.transactions
    val users = state.users

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = top + 8.dp,
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "profile") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(state.avatar, name, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                    Text("@$userId", color = Brand.InkSecondary, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = { onAction(MeAction.LogOut) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Brand.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Brand.Card, contentColor = Brand.Ink)
                ) { Text("Log out") }
            }
        }
        item(key = "balance") {
            when (user) {
                is LiveState.Unreachable -> UnreachableCard(user.message)

                is LiveState.Ready ->
                    if (user.value == null) {
                        MessageCard(
                            emoji = "🤔",
                            title = "Account not found",
                            message = "users/$userId no longer exists. Log out and sign in again."
                        )
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
                color = Brand.Ink
            )
        }
        when (transactions) {
            LiveState.Loading -> item(key = "loading") { LoadingCard() }

            is LiveState.Unreachable -> item(key = "error") { UnreachableCard(transactions.message) }

            is LiveState.Ready ->
                if (transactions.value.isEmpty()) {
                    item(key = "empty") {
                        MessageCard("💸", "No payments yet", "Payments you send or receive show up here.")
                    }
                } else {
                    items(transactions.value, key = { it.id }) { transaction ->
                        FeedRow(transaction, users, now, highlighted = false, modifier = Modifier.animateItem())
                    }
                }
        }
    }
}

@Composable
private fun BalanceCard(balanceCents: Long?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().background(Brand.Card, RoundedCornerShape(28.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Balance", color = Brand.InkSecondary, fontSize = 15.sp)
        Text(
            balanceCents?.let(Money::format) ?: "—",
            color = Brand.Ink,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Mock wallet for the demo", color = Brand.InkSecondary, fontSize = 13.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F3F5)
@Composable
private fun MeScreenPreview() {
    SplitPartyTheme {
        MeScreen(
            MeUiState(
                userId = "maxi",
                displayName = "Maxi",
                avatar = "avatar_maxi",
                profile = LiveState.Ready(PreviewData.maxi),
                transactions = LiveState.Ready(PreviewData.transactions),
                users = PreviewData.users
            ),
            contentPadding = PaddingValues(),
            onAction = {}
        )
    }
}
