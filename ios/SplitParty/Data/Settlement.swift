import Foundation

/// What the host's Pay writes (docs/firestore-schema.md, "Paying a party"): one
/// payment from every joinee to the host for their share under the split rule.
/// Pure values, so the Firestore writer and the test fixture settle identically
/// and the rule can be unit tested.
struct PartySettlement: Equatable {
    struct Payment: Equatable {
        /// `transactions/{partyId}_{payerId}`: deterministic, so a retried Pay can
        /// never create a second payment for the same member.
        let transactionId: String
        let payerId: String
        let amountCents: Int
    }

    /// The memo the rules accept is 1–80 characters.
    static let maxMemoLength = 80
    static let memoSuffix = " Split Party"
    /// `transactions.source` for payments made by Pay (the rules accept only this).
    static let transactionSource = "party"

    let partyId: String
    let hostId: String
    let merchantId: String
    let merchantName: String
    /// Joinees in join order. A share of 0 cents (a tiny bill split many ways) is
    /// left out: the rules demand a positive amount, and there is nothing to move.
    let payments: [Payment]
    /// The tip written with the paid party: the chosen one, or the 0% custom tip
    /// when none was chosen (the rules demand a non-null tip on a paid party).
    let tip: TipChoice
    /// The host's balance goes up by exactly this.
    let collectedCents: Int

    /// Nil when the party has no bill yet.
    init?(party: SplitParty, merchantName: String) {
        guard let split = party.split else { return nil }
        partyId = party.id
        hostId = party.hostId
        merchantId = party.merchantId
        self.merchantName = merchantName
        payments = party.joineeIds.compactMap { memberId in
            guard let share = split.share(of: memberId), share > 0 else { return nil }
            return Payment(transactionId: Self.transactionId(partyId: party.id, payerId: memberId),
                           payerId: memberId, amountCents: share)
        }
        tip = party.tip ?? .zero
        collectedCents = payments.reduce(0) { $0 + $1.amountCents }
    }

    var memo: String {
        Self.memo(merchantName: merchantName)
    }

    /// "Tony's Split Party". A very long merchant name is cut so the memo stays
    /// within the rules' 80 characters (counted in UTF-16 units, which is never
    /// fewer than the characters the rules count).
    static func memo(merchantName: String) -> String {
        let room = maxMemoLength - memoSuffix.utf16.count
        var name = merchantName.trimmingCharacters(in: .whitespaces)
        while name.utf16.count > room { name.removeLast() }
        if name.isEmpty { name = DemoMerchant.fallbackName }
        return name + memoSuffix
    }

    static func transactionId(partyId: String, payerId: String) -> String {
        "\(partyId)_\(payerId)"
    }

    func payment(of userId: String) -> Payment? {
        payments.first { $0.payerId == userId }
    }
}
