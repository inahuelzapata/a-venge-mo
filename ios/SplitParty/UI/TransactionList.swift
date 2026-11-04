import SwiftUI

/// A list of payment cards plus its loading, empty and unreachable states.
/// Shared by Home (everyone's payments) and Me (the signed-in user's own).
struct TransactionList: View {
    let state: ListState
    let emptyTitle: String
    let emptyMessage: String

    @Environment(LiveDataStore.self) private var liveData

    /// Relative times ("5 minutes ago") are refreshed this often.
    private static let relativeTimeRefreshInterval: TimeInterval = 30

    var body: some View {
        switch state {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 200)
        case let .unreachable(message):
            StatusCard(systemImage: "wifi.exclamationmark", title: "Can't load payments",
                       message: message, showsReconnecting: true)
                .accessibilityIdentifier("feedErrorState")
        case let .loaded(transactions) where transactions.isEmpty:
            StatusCard(systemImage: "tray", title: emptyTitle, message: emptyMessage, showsReconnecting: false)
                .accessibilityIdentifier("feedEmptyState")
        case let .loaded(transactions):
            TimelineView(.periodic(from: .now, by: Self.relativeTimeRefreshInterval)) { timeline in
                LazyVStack(spacing: 12) {
                    ForEach(transactions) { transaction in
                        TransactionRow(transaction: transaction,
                                       payer: liveData.usersById[transaction.payerId],
                                       payee: liveData.usersById[transaction.payeeId],
                                       now: timeline.date,
                                       isHighlighted: liveData.highlightedTransactionIds.contains(transaction.id))
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                }
                .animation(.spring(duration: 0.35), value: transactions.map(\.id))
            }
        }
    }
}

/// One feed card, laid out like the reference: time on top, then the sentence
/// "(A) Annie paid (M) Maxi for Tony's Split Party", then like/comment buttons.
/// The amount sits top right, because the PoC shows amounts in the feed.
struct TransactionRow: View {
    let transaction: PaymentTransaction
    let payer: UserAccount?
    let payee: UserAccount?
    let now: Date
    let isHighlighted: Bool

    private static let sentenceFontSize: CGFloat = 20
    private static let inlineAvatarSize: CGFloat = 22

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(RelativeTime.describe(transaction.createdAt, now: now))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.secondaryText)
                Spacer()
                Text(Money.format(cents: transaction.amountCents))
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.primaryText)
            }
            sentence
                .font(.system(size: Self.sentenceFontSize))
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                CounterChip(systemImage: "bubble.left", count: transaction.commentsCount, label: "comments")
                CounterChip(systemImage: "heart", count: transaction.likesCount, label: "likes")
            }
        }
        .padding(Theme.cardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Theme.cardCornerRadius)
                .fill(isHighlighted ? Theme.newRowHighlight : Theme.card)
        )
        .padding(.horizontal, Theme.screenGutter)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("transactionRow")
    }

    /// Names, never "You": every phone must show the identical feed text.
    private var sentence: Text {
        let payerName = payer?.displayName ?? transaction.payerId
        let payeeName = payee?.displayName ?? transaction.payeeId
        var text = avatar(for: payer, fallbackName: payerName) + Text(" \(payerName)")
            + Text(" paid ").foregroundColor(Theme.secondaryText)
            + avatar(for: payee, fallbackName: payeeName) + Text(" \(payeeName)")
        if !transaction.memo.isEmpty {
            text = text + Text(" for ").foregroundColor(Theme.secondaryText) + Text(transaction.memo)
        }
        return text.foregroundColor(Theme.primaryText)
    }

    private func avatar(for user: UserAccount?, fallbackName: String) -> Text {
        let image = AvatarView.inlineImage(avatarKey: user?.avatarKey ?? "",
                                           displayName: user?.displayName ?? fallbackName,
                                           size: Self.inlineAvatarSize)
        // Lowered so the circle sits on the text's baseline like the reference.
        return Text(image).baselineOffset(-4)
    }
}

/// The round grey like/comment buttons of the reference, with the mock count.
private struct CounterChip: View {
    let systemImage: String
    let count: Int
    let label: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .medium))
            Text("\(count)")
                .font(.system(size: 15, weight: .medium))
                .monospacedDigit()
        }
        .foregroundStyle(Theme.primaryText)
        .padding(.horizontal, 16)
        .frame(height: 44)
        .background(Capsule().fill(Theme.actionBackground))
        .accessibilityLabel("\(count) \(label)")
    }
}

/// Empty and error states: a card that says what is going on.
struct StatusCard: View {
    let systemImage: String
    let title: String
    let message: String
    let showsReconnecting: Bool

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 36))
                .foregroundStyle(Theme.brandBlue)
            Text(title)
                .font(.system(size: 20, weight: .semibold))
            Text(message)
                .font(.system(size: 15))
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            if showsReconnecting {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Reconnecting…").font(.system(size: 15)).foregroundStyle(Theme.secondaryText)
                }
                .padding(.top, 4)
            }
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius).fill(Theme.card))
        .padding(.horizontal, Theme.screenGutter)
    }
}

enum RelativeTime {
    /// Below this age a payment just says "Just now" (also covers a phone clock
    /// that runs slightly behind the server's).
    private static let justNowThreshold: TimeInterval = 60

    private static let formatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        // Same wording on every demo phone, whatever its language setting.
        formatter.locale = Locale(identifier: "en_US")
        return formatter
    }()

    /// "Just now", "12 hours ago", "6 days ago". A nil date is a payment whose
    /// server timestamp has not arrived yet, which means it just happened.
    static func describe(_ date: Date?, now: Date) -> String {
        guard let date, now.timeIntervalSince(date) >= justNowThreshold else { return "Just now" }
        return formatter.localizedString(for: date, relativeTo: now)
    }
}
