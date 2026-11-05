import Foundation

/// One delivery from a live single-document listener.
struct LiveDocument<Item> {
    /// Nil when the document does not exist (or no longer parses).
    let item: Item?
    /// True while the value has not been confirmed by the server, e.g. offline.
    let isFromCache: Bool
}

/// Split Party reads and writes (PoC-7 to PoC-11, FR-2.5, FR-3 to FR-5): the
/// `parties` and `joinCodes` documents of docs/firestore-schema.md, plus the
/// payments and balances Pay writes.
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

    /// Host only: sets the pre-tip bill. If no tip was chosen yet, the same update
    /// stores the 0% tip, so no phone ever sees a bill without a tip state.
    /// Refused once the party is no longer open. Throws `PartyActionError`.
    func setBill(partyId: String, billCents: Int) async throws

    /// Host only: replaces the tip. Throws `PartyActionError`.
    func setTip(partyId: String, tip: TipChoice) async throws

    /// Drops a non-host member from `memberIds` and `members` in one transaction,
    /// so a join landing at the same moment is kept. Used both for the host
    /// removing someone and for a member leaving. Throws `PartyActionError`.
    func removeMember(partyId: String, memberId: String) async throws

    /// Host only, Pay (FR-5.1–5.3): in ONE transaction, a payment from every
    /// joinee to the host for their share (`PartySettlement`), the balances moved,
    /// every member marked paid and the party set to `paid`. All or nothing.
    /// Succeeds without writing when the party is already paid, so a double tap
    /// or a retry after a dropped connection never charges twice. Throws
    /// `PartyActionError` (expired, no bill or no friend, rules, network).
    func settleParty(id: String) async throws
}
