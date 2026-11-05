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
                DemoModeCard(isAtMerchant: Bindable(appModel).isDemoAtMerchant)
                Text("Your payments")
                    .font(.system(size: 20, weight: .semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                TransactionList(state: liveData.myTransactions,
                                emptyTitle: "No payments yet",
                                emptyMessage: "Payments you send or receive show up here.")
                // Last on the tab, under a "Demo" heading: easy for the presenter
                // to find, out of a joinee's way.
                DemoResetSection(repository: liveData.parties.repository, connection: liveData.parties.connection,
                                 onResetDone: liveData.parties.replayMerchantCardEntrance)
                    .padding(.top, 16)
            }
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .accessibilityIdentifier("meScreen")
    }
}

private struct ProfileCard: View {
    let user: UserAccount?
    let userId: String
    let onLogOut: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                // Placeholder dash until the user document has loaded. A Pay or
                // a reset rolls the digits from the old value to the new one
                // (through zero to "-$12.34" if that is where it lands); with
                // Reduce Motion the new value simply replaces the old.
                Text(user.map { Money.format(cents: $0.balanceCents) } ?? "—")
                    .font(.system(size: 36, weight: .bold))
                    .monospacedDigit()
                    .contentTransition(reduceMotion ? .identity : .numericText(value: Double(user?.balanceCents ?? 0)))
                    .animation(reduceMotion ? nil : .spring(duration: 0.8), value: user?.balanceCents)
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

/// The geofence stand-in (PoC-7): while on, Home shows the merchant card as if
/// the phone had just arrived at Tony's. Remembered across restarts.
private struct DemoModeCard: View {
    @Binding var isAtMerchant: Bool

    static let title = "Demo mode: at Tony's"

    var body: some View {
        Toggle(isOn: $isAtMerchant) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Self.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.primaryText)
                Text("Pretend this phone is at the merchant")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.secondaryText)
            }
        }
        .tint(Theme.brandBlue)
        .padding(.horizontal, Theme.cardPadding)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius).fill(Theme.card))
        .padding(.horizontal, Theme.screenGutter)
        .accessibilityIdentifier("demoModeToggle")
    }
}

/// PoC-12: puts the whole demo back to its seeded state from any phone, via the
/// backend's `resetDemo` function. Nothing changes on the phone itself; every
/// phone's live listeners pick the reset up (feed, balances, parties).
private struct DemoResetSection: View {
    let repository: any PaymentDataSource
    let connection: ConnectionMonitor
    /// The demo starts over: Home plays the merchant card's arrival again.
    var onResetDone: () -> Void = {}

    @State private var isConfirming = false
    @State private var isResetting = false
    @State private var errorMessage: String?
    @State private var didSucceed = false

    /// How long "Demo reset done" stays under the button.
    private static let successNoticeDuration: Duration = .seconds(4)

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Demo")
                .font(.system(size: 20, weight: .semibold))
                .accessibilityAddTraits(.isHeader)
                .padding(.horizontal, 12)
            VStack(alignment: .leading, spacing: 12) {
                Text("Puts every phone back to the seeded feed and balances and ends any party. Everyone stays signed in.")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button { isConfirming = true } label: {
                    HStack(spacing: 8) {
                        if isResetting {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.counterclockwise")
                        }
                        Text(isResetting ? "Resetting…" : "Demo reset")
                    }
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.errorText)
                    .frame(maxWidth: .infinity, minHeight: Theme.totalActionHeight)
                    .background(Capsule().fill(Theme.actionBackground))
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(isResetting)
                .accessibilityLabel(isResetting ? "Resetting the demo" : "Demo reset")
                .accessibilityHint("Asks for confirmation, then restores the seeded demo on every phone")
                .accessibilityIdentifier("demoResetButton")
                if let errorMessage {
                    Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.errorText)
                        .accessibilityIdentifier("demoResetError")
                } else if didSucceed {
                    Label("Demo reset done", systemImage: "checkmark.circle.fill")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.brandBlue)
                        .accessibilityIdentifier("demoResetDone")
                }
            }
            .padding(Theme.cardPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius).fill(Theme.card))
        }
        .padding(.horizontal, Theme.screenGutter)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("demoSection")
        .confirmationDialog("Reset the demo?", isPresented: $isConfirming, titleVisibility: .visible) {
            Button("Reset demo", role: .destructive, action: reset)
                .accessibilityIdentifier("confirmDemoReset")
        } message: {
            Text("All parties and new payments are removed and balances go back to their seeded values on every phone.")
        }
        .sensoryFeedback(trigger: didSucceed) { _, new in new ? .success : nil }
        .sensoryFeedback(.error, trigger: errorMessage) { _, new in new != nil }
    }

    private func reset() {
        errorMessage = nil
        didSucceed = false
        guard connection.isNetworkAvailable else {
            errorMessage = DemoResetError.offline.message
            return
        }
        isResetting = true
        Task {
            defer { isResetting = false }
            do {
                try await repository.resetDemo()
                onResetDone()
                didSucceed = true
                UIAccessibility.post(notification: .announcement, argument: "Demo reset done")
                try? await Task.sleep(for: Self.successNoticeDuration)
                didSucceed = false
            } catch {
                let message = (error as? DemoResetError ?? .unreachable).message
                errorMessage = message
                UIAccessibility.post(notification: .announcement, argument: message)
            }
        }
    }
}
