import FirebaseFirestore
import Foundation

/// One delivery from a live list query.
struct LiveList<Item> {
    let items: [Item]
    /// True while Firestore has not confirmed the rows with the server. With the
    /// memory-only cache this means "we could not reach the database yet".
    let isFromCache: Bool
}

/// All Firestore access for the app shell. Uses only the collections, fields and
/// queries documented in docs/firestore-schema.md.
final class PaymentRepository {
    enum LoginError: Error, Equatable {
        case unknownUsername
        case databaseUnreachable
    }

    /// Login is a single server read; give up after this long so an unreachable
    /// database shows an error instead of a spinner that never ends.
    private static let loginTimeout: Duration = .seconds(5)

    let firestore: Firestore

    init(firestore: Firestore) {
        self.firestore = firestore
    }

    // MARK: Login

    /// `users/{normalized username}` must exist. The server is asked directly (not
    /// the cache) so a typo never signs in from stale data.
    func signIn(username rawUsername: String) async throws -> UserAccount {
        guard let username = Username.normalized(rawUsername) else {
            throw LoginError.unknownUsername
        }
        let reference = firestore.collection(Schema.Collection.users).document(username)
        let snapshot = try await withTimeout(Self.loginTimeout) {
            try await reference.getDocument(source: .server)
        }
        guard snapshot.exists, let data = snapshot.data(),
              let account = UserAccount(id: snapshot.documentID, data: data) else {
            throw LoginError.unknownUsername
        }
        return account
    }

    // MARK: Live queries

    /// Feed: `transactions orderBy(createdAt desc) limit(50)`, all users.
    func listenToFeed(_ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> ListenerRegistration {
        let query = firestore.collection(Schema.Collection.transactions)
            .order(by: Schema.TransactionField.createdAt, descending: true)
            .limit(to: Schema.listLimit)
        return listen(to: query, onChange)
    }

    /// Me tab: `transactions where(participantIds array-contains uid)
    /// orderBy(createdAt desc) limit(50)`. Uses the composite index declared in
    /// backend/firestore.indexes.json.
    func listenToTransactions(involving userId: String,
                              _ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> ListenerRegistration {
        let query = firestore.collection(Schema.Collection.transactions)
            .whereField(Schema.TransactionField.participantIds, arrayContains: userId)
            .order(by: Schema.TransactionField.createdAt, descending: true)
            .limit(to: Schema.listLimit)
        return listen(to: query, onChange)
    }

    /// All users, keyed by id: the feed needs every payer's and payee's display
    /// name and avatar, and the Me tab needs the live balance. Three seeded users,
    /// so one small listener is cheaper than a read per row.
    func listenToUsers(_ onChange: @escaping (Result<[String: UserAccount], Error>) -> Void)
        -> ListenerRegistration {
        firestore.collection(Schema.Collection.users)
            .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                if let error { return onChange(.failure(error)) }
                guard let snapshot else { return }
                var usersById: [String: UserAccount] = [:]
                for document in snapshot.documents {
                    if let user = UserAccount(id: document.documentID, data: document.data()) {
                        usersById[user.id] = user
                    }
                }
                onChange(.success(usersById))
            }
    }

    /// Forces an immediate reconnect. Firestore retries with growing backoff while
    /// offline; when the phone's network comes back we don't want to wait for it.
    func reconnectNow() {
        firestore.disableNetwork { [firestore] _ in
            firestore.enableNetwork { _ in }
        }
    }

    // MARK: Helpers

    private func listen(to query: Query,
                        _ onChange: @escaping (Result<LiveList<PaymentTransaction>, Error>) -> Void)
        -> ListenerRegistration {
        // Metadata changes are included so a "cache → server confirmed" flip is
        // delivered; that is how the screens know they are connected.
        query.addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
            if let error { return onChange(.failure(error)) }
            guard let snapshot else { return }
            let items = snapshot.documents.compactMap { document in
                PaymentTransaction(
                    id: document.documentID,
                    data: document.data(),
                    // `.estimate` fills a pending serverTimestamp() with the local
                    // time, so a just-written payment sorts at the top immediately.
                    createdAt: (document.get(Schema.TransactionField.createdAt,
                                             serverTimestampBehavior: .estimate) as? Timestamp)?.dateValue()
                )
            }
            onChange(.success(LiveList(items: items, isFromCache: snapshot.metadata.isFromCache)))
        }
    }

    private func withTimeout<Value: Sendable>(_ timeout: Duration,
                                              _ operation: @escaping @Sendable () async throws -> Value)
        async throws -> Value {
        try await withThrowingTaskGroup(of: Value.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(for: timeout)
                throw LoginError.databaseUnreachable
            }
            defer { group.cancelAll() }
            do {
                guard let first = try await group.next() else { throw LoginError.databaseUnreachable }
                return first
            } catch let error as LoginError {
                throw error
            } catch {
                // Any Firestore error on a server read here means we could not ask.
                throw LoginError.databaseUnreachable
            }
        }
    }
}
