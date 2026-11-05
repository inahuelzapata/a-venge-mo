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

    // Split Party state (see the "Parties" extension below).
    private var parties: [String: SplitParty] = [:]
    private var openPartyListeners: [UUID: (merchantId: String,
                                            onChange: (Result<LiveList<SplitParty>, Error>) -> Void)] = [:]
    private var partyListeners: [UUID: (partyId: String,
                                        onChange: (Result<LiveDocument<SplitParty>, Error>) -> Void)] = [:]
    private var nextPartyNumber = 1
    /// When set, the other seeded users join a party the signed-in user starts,
    /// one after another at this interval (UI tests and captures).
    var friendsJoinInterval: Double?
    /// Runs once, after the next successful sign-in (UI tests' scripted changes).
    var afterSignIn: (() -> Void)?

    init(scenario: Scenario, now: Date = .now) {
        self.scenario = scenario
        usersById = Self.seededUsers
        transactions = scenario == .seeded ? Self.seededTransactions(now: now) : []
    }

    /// Every party the fixture creates or hosts uses this code, so UI tests and
    /// captures know which option is the right one.
    static let fixedJoinCode = JoinCode(["🍕", "🍺", "🎉"])!

    // MARK: PaymentDataSource

    func signIn(username: String) async throws -> UserAccount {
        guard scenario != .unreachable else { throw LoginError.databaseUnreachable }
        guard let userId = Username.normalized(username), let user = usersById[userId] else {
            throw LoginError.unknownUsername
        }
        afterSignIn?()
        afterSignIn = nil
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
        "maxi": UserAccount(id: "maxi", displayName: "Maxi", avatarKey: "avatar_maxi", balanceCents: 25000,
                            friendIds: ["annie", "christophe"]),
        "annie": UserAccount(id: "annie", displayName: "Annie", avatarKey: "avatar_annie", balanceCents: 18050,
                             friendIds: ["maxi", "christophe"]),
        "christophe": UserAccount(id: "christophe", displayName: "Christophe", avatarKey: "avatar_christophe",
                                  balanceCents: 32075, friendIds: ["maxi", "annie"]),
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

// MARK: Parties

extension FixtureDataSource {
    func listenToOpenParties(merchantId: String,
                             _ onChange: @escaping (Result<LiveList<SplitParty>, Error>) -> Void)
        -> LiveSubscription {
        let id = UUID()
        openPartyListeners[id] = (merchantId, onChange)
        onChange(openPartiesResult(merchantId: merchantId))
        return FixtureSubscription { [weak self] in self?.openPartyListeners[id] = nil }
    }

    func listenToParty(id partyId: String,
                       _ onChange: @escaping (Result<LiveDocument<SplitParty>, Error>) -> Void)
        -> LiveSubscription {
        let id = UUID()
        partyListeners[id] = (partyId, onChange)
        onChange(.success(LiveDocument(item: parties[partyId], isFromCache: false)))
        return FixtureSubscription { [weak self] in self?.partyListeners[id] = nil }
    }

    func listenToMerchantName(merchantId: String, _ onChange: @escaping (String) -> Void) -> LiveSubscription {
        onChange(DemoMerchant.fallbackName)
        return FixtureSubscription {}
    }

    func startParty(hostId: String, merchantId: String) async throws -> SplitParty {
        guard scenario != .unreachable else { throw PartyActionError.unreachable }
        let now = Date()
        if let existing = parties.values.first(where: { $0.isActive(at: now) && $0.isMember(hostId) }) {
            return existing
        }
        let party = addParty(hostId: hostId, memberIds: [hostId], createdAt: now)
        if let friendsJoinInterval {
            scheduleJoins(of: Self.seededUsers.keys.sorted().filter { $0 != hostId },
                          into: party.id, every: friendsJoinInterval)
        }
        return party
    }

    func joinParty(id: String, userId: String) async throws {
        guard let party = parties[id] else { throw PartyActionError.partyNoLongerOpen }
        guard !party.isLockedOut(userId) else { throw PartyActionError.lockedOut }
        guard party.isActive(at: Date()) else { throw PartyActionError.partyNoLongerOpen }
        guard !party.isMember(userId) else { return }
        replace(party, memberIds: party.memberIds + [userId])
    }

    func recordWrongCode(partyId: String, userId: String) async throws {
        guard let party = parties[partyId] else { return }
        var attempts = party.wrongCodeAttempts
        attempts[userId, default: 0] += 1
        replace(party, wrongCodeAttempts: attempts)
    }

    func expireParty(id: String) async {
        guard let party = parties[id], party.status == .open, Date() >= party.expiresAt else { return }
        replace(party, status: .expired)
    }

    func setBill(partyId: String, billCents: Int) async throws {
        let party = try openFixtureParty(partyId)
        replace(party, billCents: billCents, tip: party.tip ?? .zero)
    }

    func setTip(partyId: String, tip: TipChoice) async throws {
        replace(try openFixtureParty(partyId), tip: tip)
    }

    func removeMember(partyId: String, memberId: String) async throws {
        let party = try openFixtureParty(partyId)
        guard memberId != party.hostId, party.isMember(memberId) else { return }
        replace(party, memberIds: party.memberIds.filter { $0 != memberId })
    }

    private func openFixtureParty(_ partyId: String) throws -> SplitParty {
        guard scenario != .unreachable else { throw PartyActionError.unreachable }
        guard let party = parties[partyId], party.isActive(at: Date()) else {
            throw PartyActionError.partyNoLongerOpen
        }
        return party
    }

    // MARK: Test controls

    /// A party at Tony's that another phone has just started (or started
    /// `ageSeconds` ago), as if read from the database.
    @discardableResult
    func addParty(hostId: String, memberIds: [String], createdAt: Date = .now, billCents: Int? = nil,
                  tip: TipChoice? = nil) -> SplitParty {
        let party = SplitParty(id: "fixture_party_\(nextPartyNumber)", hostId: hostId, merchantId: DemoMerchant.id,
                               joinCode: Self.fixedJoinCode, status: .open, createdAt: createdAt,
                               expiresAt: createdAt.addingTimeInterval(TimeInterval(PartySchema.partyLifetimeSeconds)),
                               memberIds: memberIds, wrongCodeAttempts: [:], billCents: billCents, tip: tip)
        nextPartyNumber += 1
        store(party)
        return party
    }

    func scheduleJoins(of userIds: [String], into partyId: String, every interval: Double) {
        Task { @MainActor [weak self] in
            for userId in userIds {
                try? await Task.sleep(for: .seconds(interval))
                try? await self?.joinParty(id: partyId, userId: userId)
            }
        }
    }

    // MARK: Helpers

    /// Stores a copy of `party` with the given fields changed; nil keeps a field.
    private func replace(_ party: SplitParty, status: PartyStatus? = nil, memberIds: [String]? = nil,
                         wrongCodeAttempts: [String: Int]? = nil, billCents: Int? = nil, tip: TipChoice? = nil) {
        store(SplitParty(id: party.id, hostId: party.hostId, merchantId: party.merchantId, joinCode: party.joinCode,
                         status: status ?? party.status, createdAt: party.createdAt, expiresAt: party.expiresAt,
                         memberIds: memberIds ?? party.memberIds,
                         wrongCodeAttempts: wrongCodeAttempts ?? party.wrongCodeAttempts,
                         billCents: billCents ?? party.billCents, tip: tip ?? party.tip))
    }

    private func store(_ party: SplitParty) {
        parties[party.id] = party
        openPartyListeners.values.forEach { $0.onChange(openPartiesResult(merchantId: $0.merchantId)) }
        partyListeners.values.filter { $0.partyId == party.id }
            .forEach { $0.onChange(.success(LiveDocument(item: party, isFromCache: false))) }
    }

    private func openPartiesResult(merchantId: String) -> Result<LiveList<SplitParty>, Error> {
        guard scenario != .unreachable else { return .failure(FixtureUnreachableError()) }
        let open = parties.values.filter { $0.merchantId == merchantId && $0.status == .open }
            .sorted { $0.createdAt < $1.createdAt }
        return .success(LiveList(items: open, isFromCache: false))
    }
}

private struct FixtureSubscription: LiveSubscription {
    let onCancel: () -> Void

    func cancel() {
        onCancel()
    }
}
#endif
