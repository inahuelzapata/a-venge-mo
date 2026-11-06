import Foundation

/// Party and join-code values from docs/firestore-schema.md (`parties`, `joinCodes`,
/// emoji palette, lifecycle). Same constants as backend/lib/schema.js.
enum PartySchema {
    enum Collection {
        static let parties = "parties"
        static let joinCodes = "joinCodes"
        static let merchants = "merchants"
    }

    enum PartyField {
        static let hostId = "hostId"
        static let merchantId = "merchantId"
        static let joinCode = "joinCode"
        static let status = "status"
        static let createdAt = "createdAt"
        static let expiresAt = "expiresAt"
        static let paidAt = "paidAt"
        static let billCents = "billCents"
        static let tip = "tip"
        static let memberIds = "memberIds"
        static let members = "members"
        static let wrongCodeAttempts = "wrongCodeAttempts"
        static let cancelledAt = "cancelledAt"
        static let cancelledBy = "cancelledBy"
    }

    enum MemberField {
        static let joinedAt = "joinedAt"
        static let paid = "paid"
    }

    enum LockField {
        static let merchantId = "merchantId"
        static let joinCode = "joinCode"
        static let partyId = "partyId"
        static let expiresAt = "expiresAt"
    }

    enum MerchantField {
        static let name = "name"
    }

    /// A party lives exactly this long; the rules reject any other `expiresAt`.
    static let partyLifetimeSeconds: Int64 = 5 * 60
    static let maxWrongCodeAttempts = 3
    /// Rules cap `memberIds` at 8 (they cannot loop over a list).
    static let maxMembers = 8

    /// `joinCodes/{merchantId}_{e1}{e2}`, e.g. "tonys-greenwich_🍕🍺".
    static func joinCodeLockId(merchantId: String, code: JoinCode) -> String {
        "\(merchantId)_\(code.emojis.joined())"
    }
}

/// The demo's single merchant. The "Demo mode: at Tony's" switch stands in for
/// the geofence match at this merchant (backend/lib/seedData.js MERCHANT_ID).
enum DemoMerchant {
    static let id = "tonys-greenwich"
    /// Shown until `merchants/tonys-greenwich` has loaded, and if it never does.
    static let fallbackName = "Tony's"
}

/// `parties.status`. `cancelled` (host only, schema "Cancelling a party") is its
/// own state: it is never shown as expired or paid.
enum PartyStatus: String {
    case open, paid, expired, cancelled
}

/// The two-emoji join code. Every emoji is one code point from the palette, so
/// iOS, Android and the rules compare the same strings.
struct JoinCode: Hashable {
    /// Two emojis from a 12-emoji palette give 144 codes per merchant.
    static let length = 2

    /// The schema's 12-emoji palette with the names VoiceOver reads for each.
    static let palette: [(emoji: String, name: String)] = [
        ("🍕", "pizza"), ("🍔", "burger"), ("🌮", "taco"), ("🍣", "sushi"),
        ("🍩", "doughnut"), ("🍺", "beer"), ("🎉", "party popper"), ("🔥", "fire"),
        ("🚀", "rocket"), ("🎸", "guitar"), ("🐶", "dog"), ("🌵", "cactus"),
    ]
    private static let paletteEmojis = palette.map(\.emoji)
    private static let namesByEmoji = Dictionary(uniqueKeysWithValues: palette.map { ($0.emoji, $0.name) })

    let emojis: [String]

    /// Nil unless exactly `length` palette emojis, so a malformed document, or one
    /// written by an older build with a different code length, is skipped rather than crashing.
    init?(_ emojis: [String]) {
        guard emojis.count == Self.length, emojis.allSatisfy(Self.paletteEmojis.contains) else { return nil }
        self.emojis = emojis
    }

    /// Repeats are allowed, as in the schema.
    static func random<Generator: RandomNumberGenerator>(using generator: inout Generator) -> JoinCode {
        JoinCode((0..<length).map { _ in paletteEmojis.randomElement(using: &generator)! })!
    }

    static func random() -> JoinCode {
        var generator = SystemRandomNumberGenerator()
        return random(using: &generator)
    }

    /// "pizza, beer": what VoiceOver says for the code.
    var spokenNames: String {
        emojis.map { Self.namesByEmoji[$0] ?? $0 }.joined(separator: ", ")
    }

    static func spokenName(of emoji: String) -> String {
        namesByEmoji[emoji] ?? emoji
    }
}

/// The four choices on the code-confirmation screen: the real code plus three
/// decoys from the same palette, all different, in random order.
enum JoinCodeChoices {
    static let optionCount = 4

    /// When `previous` is given the new arrangement is guaranteed to differ from
    /// it, so a wrong pick always visibly reshuffles.
    static func make<Generator: RandomNumberGenerator>(for code: JoinCode, previous: [JoinCode]? = nil,
                                                       using generator: inout Generator) -> [JoinCode] {
        while true {
            var options: Set<JoinCode> = [code]
            while options.count < optionCount {
                options.insert(JoinCode.random(using: &generator))
            }
            let arranged = Array(options).shuffled(using: &generator)
            if arranged != previous { return arranged }
        }
    }

    static func make(for code: JoinCode, previous: [JoinCode]? = nil) -> [JoinCode] {
        var generator = SystemRandomNumberGenerator()
        return make(for: code, previous: previous, using: &generator)
    }
}

/// `parties/{id}`, with what the app reads.
struct SplitParty: Equatable, Identifiable {
    let id: String
    let hostId: String
    let merchantId: String
    let joinCode: JoinCode
    let status: PartyStatus
    let createdAt: Date
    let expiresAt: Date
    /// Host first, then joinees in join order.
    let memberIds: [String]
    let wrongCodeAttempts: [String: Int]
    /// Pre-tip bill in cents; nil until the host confirms a total.
    let billCents: Int?
    /// Nil until the first bill is confirmed (shown as 0%).
    let tip: TipChoice?
    /// When the host tapped Pay; nil until then (or while the server time is pending).
    let paidAt: Date?
    /// Members whose `members.{uid}.paid` is true. Pay sets every member at once.
    let paidMemberIds: Set<String>
    /// When the host cancelled; nil unless `status == .cancelled` (or while the server time is pending).
    let cancelledAt: Date?

    /// Nil when a required field is missing or malformed.
    init?(id: String, data: [String: Any], dateOf: (Any?) -> Date?) {
        guard let hostId = data[PartySchema.PartyField.hostId] as? String,
              let merchantId = data[PartySchema.PartyField.merchantId] as? String,
              let joinCode = (data[PartySchema.PartyField.joinCode] as? [String]).flatMap(JoinCode.init),
              let status = (data[PartySchema.PartyField.status] as? String).flatMap(PartyStatus.init),
              let createdAt = dateOf(data[PartySchema.PartyField.createdAt]),
              let expiresAt = dateOf(data[PartySchema.PartyField.expiresAt]),
              let memberIds = data[PartySchema.PartyField.memberIds] as? [String] else {
            return nil
        }
        let rawAttempts = data[PartySchema.PartyField.wrongCodeAttempts] as? [String: Any] ?? [:]
        let billCents = FirestoreValue.integer(data[PartySchema.PartyField.billCents]).flatMap { $0 >= 0 ? $0 : nil }
        let members = data[PartySchema.PartyField.members] as? [String: Any] ?? [:]
        let paidMemberIds = members.compactMap { memberId, value in
            (value as? [String: Any])?[PartySchema.MemberField.paid] as? Bool == true ? memberId : nil
        }
        self.init(id: id, hostId: hostId, merchantId: merchantId, joinCode: joinCode, status: status,
                  createdAt: createdAt, expiresAt: expiresAt, memberIds: memberIds,
                  wrongCodeAttempts: rawAttempts.compactMapValues { FirestoreValue.integer($0) },
                  billCents: billCents, tip: TipChoice(firestoreValue: data[PartySchema.PartyField.tip]),
                  paidAt: dateOf(data[PartySchema.PartyField.paidAt]), paidMemberIds: Set(paidMemberIds),
                  cancelledAt: dateOf(data[PartySchema.PartyField.cancelledAt]))
    }

    init(id: String, hostId: String, merchantId: String, joinCode: JoinCode, status: PartyStatus,
         createdAt: Date, expiresAt: Date, memberIds: [String], wrongCodeAttempts: [String: Int],
         billCents: Int? = nil, tip: TipChoice? = nil, paidAt: Date? = nil, paidMemberIds: Set<String> = [],
         cancelledAt: Date? = nil) {
        self.id = id
        self.hostId = hostId
        self.merchantId = merchantId
        self.joinCode = joinCode
        self.status = status
        self.createdAt = createdAt
        self.expiresAt = expiresAt
        self.memberIds = memberIds
        self.wrongCodeAttempts = wrongCodeAttempts
        self.billCents = billCents
        self.tip = tip
        self.paidAt = paidAt
        self.paidMemberIds = paidMemberIds
        self.cancelledAt = cancelledAt
    }

    /// Every member's share, or nil while there is no bill.
    var split: PartySplit? {
        PartySplit(billCents: billCents, tip: tip, memberIds: memberIds)
    }

    /// Open by status AND by the clock. An `open` party past `expiresAt` counts
    /// as expired even if nobody has written that yet (schema "Lifecycle").
    func isActive(at now: Date) -> Bool {
        status == .open && now < expiresAt
    }

    /// Cancelled by the host (never expired, never paid).
    var isCancelled: Bool {
        status == .cancelled
    }

    /// Whether `userId` may cancel right now: the host, while the party is open and unexpired.
    func canCancel(by userId: String, at now: Date) -> Bool {
        userId == hostId && isActive(at: now)
    }

    /// Expired by status or by the clock (a paid or cancelled party is not expired).
    func isExpired(at now: Date) -> Bool {
        status == .expired || (status == .open && now >= expiresAt)
    }

    /// Everyone but the host: the people Pay charges.
    var joineeIds: [String] {
        memberIds.filter { $0 != hostId }
    }

    /// Why Pay can't run yet, or nil when it can (open, unexpired, bill set and
    /// at least one friend in). A paid party has nothing left to pay.
    func payBlocker(at now: Date) -> PayBlocker? {
        if status == .paid { return .alreadyPaid }
        if status == .cancelled { return .cancelled }
        guard isActive(at: now) else { return .expired }
        guard billCents != nil else { return .needsTotal }
        guard !joineeIds.isEmpty else { return .needsFriends }
        return nil
    }

    func isMember(_ userId: String) -> Bool {
        memberIds.contains(userId)
    }

    func wrongAttempts(by userId: String) -> Int {
        wrongCodeAttempts[userId] ?? 0
    }

    func isLockedOut(_ userId: String) -> Bool {
        wrongAttempts(by: userId) >= PartySchema.maxWrongCodeAttempts
    }

    func secondsLeft(at now: Date) -> Int {
        max(0, Int(expiresAt.timeIntervalSince(now).rounded(.up)))
    }
}

/// Why the host's Pay button is disabled. `label` is what the button reads.
enum PayBlocker: Equatable {
    case needsTotal
    case needsFriends
    case expired
    case alreadyPaid
    case cancelled

    var label: String {
        switch self {
        case .needsTotal: "Add the total"
        case .needsFriends: "Waiting for friends to join"
        case .expired: "Party expired"
        case .alreadyPaid: "Paid"
        case .cancelled: "Party cancelled"
        }
    }
}

/// Why a party action failed, worded for the inline error.
enum PartyActionError: Error, Equatable {
    case offline
    case unreachable
    case rejected
    /// The party expired, was settled or filled up before the join landed.
    case partyNoLongerOpen
    case lockedOut
    /// Pay was asked for a party without a bill or without any friend in it.
    case notReadyToPay

    var message: String {
        switch self {
        case .offline: "You're offline. Connect and try starting the party again."
        case .unreachable: "Couldn't reach the database. Nothing was created; try again."
        case .rejected: "The database refused the party. Nothing was created; try again."
        case .partyNoLongerOpen: "This party is no longer open."
        case .lockedOut: "Too many wrong codes for this party."
        case .notReadyToPay: "Add the total and wait for a friend to join before paying."
        }
    }

    /// For Pay: says that nothing moved, so the host knows a retry is safe.
    var payMessage: String {
        switch self {
        case .offline: "You're offline. Nothing was paid; try again when you're connected."
        case .unreachable: "Couldn't reach the database. Nothing was paid; try again."
        case .rejected: "The database refused the payment. Nothing was paid."
        case .partyNoLongerOpen: "This party is no longer open. Nothing was paid."
        case .lockedOut, .notReadyToPay: message
        }
    }

    /// For the host's cancel: says the party is still running when it failed.
    var cancelMessage: String {
        switch self {
        case .offline: "You're offline. The party wasn't cancelled; try again when you're connected."
        case .unreachable: "Couldn't reach the database. The party wasn't cancelled; try again."
        case .rejected: "The database refused the cancel. The party is still open."
        case .partyNoLongerOpen: "This party is no longer open, so it can't be cancelled."
        case .lockedOut, .notReadyToPay: message
        }
    }

    /// For changes to an existing party (total, tip, removal, leaving), where
    /// "nothing was created" would be the wrong thing to say.
    var changeMessage: String {
        switch self {
        case .offline: "You're offline. Nothing was saved; try again when you're connected."
        case .unreachable: "Couldn't reach the database. Nothing was saved; try again."
        case .rejected: "The database refused the change. Nothing was saved."
        case .partyNoLongerOpen, .lockedOut, .notReadyToPay: message
        }
    }
}
