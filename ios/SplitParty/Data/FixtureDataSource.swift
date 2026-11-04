#if DEBUG
import Foundation

/// In-memory stand-in for Firestore, for snapshot and UI tests only (Debug
/// builds; never shipped). Serves the same users and payments as
/// backend/lib/seedData.js, with times relative to "now" so relative-time labels
/// ("2 days ago") never drift between runs.
@MainActor
final class FixtureDataSource: PaymentDataSource {
    enum Scenario: String, CaseIterable {
        /// The three seeded users and six seeded payments.
        case seeded
        /// Seeded users, no payments: Home and Me show the empty state.
        case empty
        /// Every read fails: login and lists show the unreachable state.
        case unreachable
    }

    let scenario: Scenario
    private var usersById: [String: UserAccount]
    private var transactions: [PaymentTransaction]
    private var feedListeners: [UUID: (Result<LiveList<PaymentTransaction>, Error>) -> Void] = [:]
    private var participantListeners: [UUID: (userId: String,
                                              onChange: (Result<LiveList<PaymentTransaction>, Error>) -> Void)] = [:]

    private struct FixtureUnreachableError: Error {}

    init(scenario: Scenario, now: Date = .now) {
        self.scenario = scenario
        usersById = Self.seededUsers
        transactions = scenario == .seeded ? Self.seededTransactions(now: now) : []
    }

    // MARK: PaymentDataSource

    func signIn(username: String) async throws -> UserAccount {
        guard scenario != .unreachable else { throw LoginError.databaseUnreachable }
        guard let userId = Username.normalized(username), let user = usersById[userId] else {
            throw LoginError.unknownUsername
        }
        return user
    }

    func listenToFeed(_ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> LiveSubscription {
        let id = UUID()
        feedListeners[id] = onChange
        onChange(feedResult())
        return FixtureSubscription { [weak self] in self?.feedListeners[id] = nil }
    }

    func listenToTransactions(involving userId: String,
                              _ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> LiveSubscription {
        let id = UUID()
        participantListeners[id] = (userId, onChange)
        onChange(transactionsResult(involving: userId))
        return FixtureSubscription { [weak self] in self?.participantListeners[id] = nil }
    }

    func listenToUsers(_ onChange: @escaping (Result<[String: UserAccount], Error>) -> Void)
        -> LiveSubscription {
        onChange(scenario == .unreachable ? .failure(FixtureUnreachableError()) : .success(usersById))
        return FixtureSubscription {}
    }

    func reconnectNow() {}

    // MARK: Test controls

    /// Adds a payment as if another phone had just written it; listeners fire.
    func insert(_ transaction: PaymentTransaction) {
        transactions.insert(transaction, at: 0)
        feedListeners.values.forEach { $0(feedResult()) }
        participantListeners.values.forEach { $0.onChange(transactionsResult(involving: $0.userId)) }
    }

    /// The payment UI tests insert while Home is open.
    static func incomingPartyPayment(now: Date = .now) -> PaymentTransaction {
        PaymentTransaction(id: "fixture_incoming", payerId: "annie", payeeId: "maxi", amountCents: 3115,
                           memo: "Tony's Split Party", merchantName: "Tony's", createdAt: now,
                           likesCount: 0, commentsCount: 0)
    }

    // MARK: Data

    private func feedResult() -> Result<LiveList<PaymentTransaction>, Error> {
        guard scenario != .unreachable else { return .failure(FixtureUnreachableError()) }
        return .success(LiveList(items: transactions, isFromCache: false))
    }

    private func transactionsResult(involving userId: String) -> Result<LiveList<PaymentTransaction>, Error> {
        feedResult().map { list in
            LiveList(items: list.items.filter { $0.payerId == userId || $0.payeeId == userId },
                     isFromCache: false)
        }
    }

    /// Same values as SEED_USERS in backend/lib/seedData.js.
    private static let seededUsers: [String: UserAccount] = [
        "maxi": UserAccount(id: "maxi", displayName: "Maxi", avatarKey: "avatar_maxi", balanceCents: 25000),
        "annie": UserAccount(id: "annie", displayName: "Annie", avatarKey: "avatar_annie", balanceCents: 18050),
        "christophe": UserAccount(id: "christophe", displayName: "Christophe", avatarKey: "avatar_christophe",
                                  balanceCents: 32075),
    ]

    /// Same payments as SEED_FEED in backend/lib/seedData.js, newest first, with
    /// fixed ages instead of fixed dates.
    private static func seededTransactions(now: Date) -> [PaymentTransaction] {
        let hour: TimeInterval = 3600
        let rows: [(String, String, String, Int, String, String?, TimeInterval, Int, Int)] = [
            ("seed_tx_06", "maxi", "annie", 900, "🍦 ice cream run", nil, 3 * hour, 1, 1),
            ("seed_tx_05", "annie", "christophe", 3000, "Uber home 🚕", nil, 12 * hour, 0, 0),
            ("seed_tx_04", "christophe", "maxi", 2575, "Tony's Split Party", "Tony's", 2 * 24 * hour, 2, 2),
            ("seed_tx_03", "maxi", "christophe", 1200, "Coffee ☕", nil, 3 * 24 * hour, 1, 0),
            ("seed_tx_02", "christophe", "annie", 4200, "Concert tickets 🎸", nil, 4 * 24 * hour, 3, 1),
            ("seed_tx_01", "annie", "maxi", 1850, "🌮 Taco Tuesday", nil, 5 * 24 * hour, 2, 0),
        ]
        return rows.map { id, payer, payee, cents, memo, merchant, age, likes, comments in
            PaymentTransaction(id: id, payerId: payer, payeeId: payee, amountCents: cents, memo: memo,
                               merchantName: merchant, createdAt: now.addingTimeInterval(-age),
                               likesCount: likes, commentsCount: comments)
        }
    }
}

private struct FixtureSubscription: LiveSubscription {
    let onCancel: () -> Void

    func cancel() {
        onCancel()
    }
}
#endif
