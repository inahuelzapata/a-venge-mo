import Foundation

/// One delivery from a live single-document listener.
struct LiveDocument<Item> {
    /// Nil when the document does not exist (or no longer parses).
    let item: Item?
    /// True while the value has not been confirmed by the server, e.g. offline.
    let isFromCache: Bool
}

/// Split Party reads and writes (PoC-7 to PoC-9). Only the `parties` and
/// `joinCodes` documents of docs/firestore-schema.md; nothing here touches
/// balances, transactions, the bill or the tip.
@MainActor
protocol PartyDataSource: AnyObject {
    /// `parties where merchantId == m && status == "open"`. Some of these may be
    /// past `expiresAt` already; callers decide by the clock (`SplitParty.isActive`).
    func listenToOpenParties(merchantId: String,
                             _ onChange: @escaping (Result<LiveList<SplitParty>, Error>) -> Void)
        -> LiveSubscription

    /// One party, whatever its status, so a screen sees it turn expired or paid.
    func listenToParty(id: String, _ onChange: @escaping (Result<LiveDocument<SplitParty>, Error>) -> Void)
        -> LiveSubscription

    /// `merchants/{id}.name`, e.g. "Tony's".
    func listenToMerchantName(merchantId: String, _ onChange: @escaping (String) -> Void) -> LiveSubscription

    /// Returns the open, unexpired party the host already belongs to at this
    /// merchant if there is one; otherwise creates one (lock + party in one
    /// transaction, retrying silently when the code is taken). Throws
    /// `PartyActionError`; a failure writes nothing.
    func startParty(hostId: String, merchantId: String) async throws -> SplitParty

    /// Appends `userId` to the party's members in a transaction, so two joins in
    /// the same instant both land. Throws `PartyActionError`.
    func joinParty(id: String, userId: String) async throws

    /// `wrongCodeAttempts.{userId} += 1` on the party being joined.
    func recordWrongCode(partyId: String, userId: String) async throws

    /// Host only: marks an open party past `expiresAt` as expired and releases its
    /// lock if the lock still belongs to it. Does nothing (and never throws) when
    /// another phone already did it, or the party is not due.
    func expireParty(id: String) async
}
