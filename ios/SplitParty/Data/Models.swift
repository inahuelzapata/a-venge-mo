import Foundation

/// Collection and field names from docs/firestore-schema.md (the contract shared
/// with Android and backend/lib/schema.js). Read exactly these, write nothing else.
enum Schema {
    enum Collection {
        static let users = "users"
        static let transactions = "transactions"
    }

    enum UserField {
        static let username = "username"
        static let displayName = "displayName"
        static let avatar = "avatar"
        static let balanceCents = "balanceCents"
    }

    enum TransactionField {
        static let payerId = "payerId"
        static let payeeId = "payeeId"
        static let participantIds = "participantIds"
        static let amountCents = "amountCents"
        static let memo = "memo"
        static let merchantName = "merchantName"
        static let partyId = "partyId"
        static let createdAt = "createdAt"
        static let likesCount = "likesCount"
        static let commentsCount = "commentsCount"
    }

    /// The documented queries cap both lists at 50 rows.
    static let listLimit = 50
}

/// `users/{id}`. `friendIds` is deliberately not read: no screen in this app shows it.
struct UserAccount: Equatable, Identifiable {
    let id: String
    let displayName: String
    let avatarKey: String
    let balanceCents: Int

    /// Returns nil when a required field is missing or has the wrong type, so a
    /// malformed document is skipped instead of crashing the app.
    init?(id: String, data: [String: Any]) {
        guard let displayName = data[Schema.UserField.displayName] as? String,
              let balanceCents = FirestoreValue.integer(data[Schema.UserField.balanceCents]) else {
            return nil
        }
        self.id = id
        self.displayName = displayName
        self.avatarKey = data[Schema.UserField.avatar] as? String ?? ""
        self.balanceCents = balanceCents
    }

    init(id: String, displayName: String, avatarKey: String, balanceCents: Int) {
        self.id = id
        self.displayName = displayName
        self.avatarKey = avatarKey
        self.balanceCents = balanceCents
    }
}

/// `transactions/{id}`, as shown in the feed and on the Me tab.
struct PaymentTransaction: Equatable, Identifiable {
    let id: String
    let payerId: String
    let payeeId: String
    let amountCents: Int
    let memo: String
    let merchantName: String?
    let createdAt: Date?
    let likesCount: Int
    let commentsCount: Int

    /// `createdAt` may be nil for a moment: a party payment written with
    /// `serverTimestamp()` arrives locally before the server fills the time in.
    init?(id: String, data: [String: Any], createdAt: Date?) {
        guard let payerId = data[Schema.TransactionField.payerId] as? String,
              let payeeId = data[Schema.TransactionField.payeeId] as? String,
              let amountCents = FirestoreValue.integer(data[Schema.TransactionField.amountCents]) else {
            return nil
        }
        self.id = id
        self.payerId = payerId
        self.payeeId = payeeId
        self.amountCents = amountCents
        self.memo = data[Schema.TransactionField.memo] as? String ?? ""
        self.merchantName = data[Schema.TransactionField.merchantName] as? String
        self.createdAt = createdAt
        self.likesCount = FirestoreValue.integer(data[Schema.TransactionField.likesCount]) ?? 0
        self.commentsCount = FirestoreValue.integer(data[Schema.TransactionField.commentsCount]) ?? 0
    }

    init(id: String, payerId: String, payeeId: String, amountCents: Int, memo: String,
         merchantName: String?, createdAt: Date?, likesCount: Int, commentsCount: Int) {
        self.id = id
        self.payerId = payerId
        self.payeeId = payeeId
        self.amountCents = amountCents
        self.memo = memo
        self.merchantName = merchantName
        self.createdAt = createdAt
        self.likesCount = likesCount
        self.commentsCount = commentsCount
    }
}

enum FirestoreValue {
    /// Firestore integers arrive as `Int64` inside `NSNumber`. A floating-point
    /// value (which the schema forbids for money) is rejected, not rounded, so a
    /// bad document is visible as missing rather than showing a wrong amount.
    static func integer(_ value: Any?) -> Int? {
        guard let number = value as? NSNumber else { return nil }
        guard CFNumberIsFloatType(number) == false else { return nil }
        return number.intValue
    }
}
