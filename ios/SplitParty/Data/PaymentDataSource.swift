import Foundation

/// One delivery from a live list query.
struct LiveList<Item> {
    let items: [Item]
    /// True while the rows have not been confirmed by the database server. With
    /// the memory-only cache this means "we could not reach the database yet".
    let isFromCache: Bool
}

enum LoginError: Error, Equatable {
    case unknownUsername
    case databaseUnreachable
}

/// An open live query; `cancel()` stops it.
protocol LiveSubscription {
    func cancel()
}

/// Where the app shell reads its data. `PaymentRepository` is the real one
/// (Firestore); `FixtureDataSource` (Debug builds only) serves fixed seeded data
/// to snapshot and UI tests so they need no database.
@MainActor
protocol PaymentDataSource: AnyObject {
    /// Returns the account for a username, or throws `LoginError`.
    func signIn(username: String) async throws -> UserAccount

    /// Everyone's transactions, newest first.
    func listenToFeed(_ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> LiveSubscription

    /// Transactions where `userId` is payer or payee, newest first.
    func listenToTransactions(involving userId: String,
                              _ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> LiveSubscription

    /// All users, keyed by id.
    func listenToUsers(_ onChange: @escaping (Result<[String: UserAccount], Error>) -> Void)
        -> LiveSubscription

    /// Reconnects immediately after the network comes back.
    func reconnectNow()
}
