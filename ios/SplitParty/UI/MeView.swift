import SwiftUI

/// Me: only the signed-in user. Name, avatar, live mock balance and their own
/// payments (as payer or payee). The friends list is deliberately not shown.
struct MeView: View {
    @Environment(AppModel.self) private var appModel
    @Environment(LiveDataStore.self) private var liveData

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Me")
                    .font(.system(size: 20, weight: .bold))
                    .padding(.top, 8)
                    .frame(height: 44)
                ProfileCard(user: liveData.signedInUser, userId: liveData.userId,
                            onLogOut: appModel.logOut)
                Text("Your payments")
                    .font(.system(size: 20, weight: .semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                TransactionList(state: liveData.myTransactions,
                                emptyTitle: "No payments yet",
                                emptyMessage: "Payments you send or receive show up here.")
            }
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
    }
}

private struct ProfileCard: View {
    let user: UserAccount?
    let userId: String
    let onLogOut: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            AvatarView(avatarKey: user?.avatarKey ?? "", displayName: user?.displayName ?? userId, size: 80)
            Text(user?.displayName ?? userId)
                .font(.system(size: 26, weight: .bold))
            Text("@\(userId)")
                .font(.system(size: 15))
                .foregroundStyle(Theme.secondaryText)

            VStack(spacing: 4) {
                Text("Balance")
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.secondaryText)
                // Placeholder dash until the user document has loaded.
                Text(user.map { Money.format(cents: $0.balanceCents) } ?? "—")
                    .font(.system(size: 36, weight: .bold))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .animation(.default, value: user?.balanceCents)
                    .accessibilityIdentifier("balance")
            }
            .padding(.vertical, 12)

            Button("Log out", action: onLogOut)
                .buttonStyle(PillButtonStyle())
        }
        .padding(Theme.cardPadding)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius).fill(Theme.card))
        .padding(.horizontal, Theme.screenGutter)
    }
}
