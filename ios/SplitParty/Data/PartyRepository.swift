import FirebaseFirestore
import Foundation

/// Firestore side of `PartyDataSource`, following docs/firestore-schema.md
/// ("Join-code uniqueness", "Lifecycle", "Joining by code").
extension PaymentRepository {
    func listenToOpenParties(merchantId: String,
                             _ onChange: @escaping (Result<LiveList<SplitParty>, Error>) -> Void)
        -> LiveSubscription {
        // Two equality filters are served by Firestore's single-field indexes; the
        // expiry check is done on the phone by the clock, which the schema
        // requires anyway (an "open" party past expiresAt is expired).
        let registration = PartyWriter.openPartiesQuery(firestore, merchantId: merchantId)
            .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                if let error { return onChange(.failure(error)) }
                guard let snapshot else { return }
                let parties = snapshot.documents.compactMap(PartyWriter.party(from:))
                onChange(.success(LiveList(items: parties, isFromCache: snapshot.metadata.isFromCache)))
            }
        return FirestoreListener(registration: registration)
    }

    func listenToParty(id: String, _ onChange: @escaping (Result<LiveDocument<SplitParty>, Error>) -> Void)
        -> LiveSubscription {
        let registration = firestore.collection(PartySchema.Collection.parties).document(id)
            .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                if let error { return onChange(.failure(error)) }
                guard let snapshot else { return }
                onChange(.success(LiveDocument(item: PartyWriter.party(from: snapshot),
                                               isFromCache: snapshot.metadata.isFromCache)))
            }
        return FirestoreListener(registration: registration)
    }

    func listenToMerchantName(merchantId: String, _ onChange: @escaping (String) -> Void) -> LiveSubscription {
        let registration = firestore.collection(PartySchema.Collection.merchants).document(merchantId)
            .addSnapshotListener { snapshot, _ in
                if let name = snapshot?.get(PartySchema.MerchantField.name) as? String, !name.isEmpty {
                    onChange(name)
                }
            }
        return FirestoreListener(registration: registration)
    }

    func startParty(hostId: String, merchantId: String) async throws -> SplitParty {
        try await PartyWriter(firestore: firestore).startParty(hostId: hostId, merchantId: merchantId)
    }

    func joinParty(id: String, userId: String) async throws {
        try await PartyWriter(firestore: firestore).joinParty(id: id, userId: userId)
    }

    func recordWrongCode(partyId: String, userId: String) async throws {
        try await PartyWriter(firestore: firestore).recordWrongCode(partyId: partyId, userId: userId)
    }

    func expireParty(id: String) async {
        await PartyWriter(firestore: firestore).expireParty(id: id)
    }

    func setBill(partyId: String, billCents: Int) async throws {
        try await PartyWriter(firestore: firestore).setBill(partyId: partyId, billCents: billCents)
    }

    func setTip(partyId: String, tip: TipChoice) async throws {
        try await PartyWriter(firestore: firestore).setTip(partyId: partyId, tip: tip)
    }

    func removeMember(partyId: String, memberId: String) async throws {
        try await PartyWriter(firestore: firestore).removeMember(partyId: partyId, memberId: memberId)
    }

    func setCustomShare(partyId: String, memberId: String, cents: Int?) async throws {
        try await PartyWriter(firestore: firestore).setCustomShare(partyId: partyId, memberId: memberId, cents: cents)
    }

    func settleParty(id: String) async throws {
        try await PartyWriter(firestore: firestore).settleParty(id: id)
    }

    func cancelParty(id: String, hostId: String) async throws -> PartyCancellation? {
        try await PartyWriter(firestore: firestore).cancelParty(id: id, hostId: hostId)
    }
}

/// The party transactions. Deliberately not main-actor isolated: Firestore runs
/// transaction blocks on its own queue, and a block inheriting the main actor
/// would be called off it.
private struct PartyWriter: @unchecked Sendable {
    let firestore: Firestore

    /// A transaction cannot finish offline; past this the user gets the inline
    /// error. A transaction is atomic, so a late commit can never leave a lock
    /// without its party (Home would then simply show "Back to your party").
    private static let writeTimeout: Duration = .seconds(10)
    /// 1728 codes per merchant: a handful of retries is plenty for a demo.
    private static let maxCodeAttempts = 12

    private var parties: CollectionReference { firestore.collection(PartySchema.Collection.parties) }
    private var joinCodes: CollectionReference { firestore.collection(PartySchema.Collection.joinCodes) }

    static func openPartiesQuery(_ firestore: Firestore, merchantId: String) -> Query {
        firestore.collection(PartySchema.Collection.parties)
            .whereField(PartySchema.PartyField.merchantId, isEqualTo: merchantId)
            .whereField(PartySchema.PartyField.status, isEqualTo: PartyStatus.open.rawValue)
    }

    static func party(from snapshot: DocumentSnapshot) -> SplitParty? {
        guard let data = snapshot.data() else { return nil }
        return SplitParty(id: snapshot.documentID, data: data) { ($0 as? Timestamp)?.dateValue() }
    }

    // MARK: Start

    func startParty(hostId: String, merchantId: String) async throws -> SplitParty {
        try await withPartyTimeout {
            if let existing = try await activeParty(of: hostId, merchantId: merchantId) {
                return existing
            }
            for _ in 0..<Self.maxCodeAttempts {
                if let created = try await createParty(hostId: hostId, merchantId: merchantId,
                                                       code: JoinCode.random()) {
                    return created
                }
            }
            throw PartyActionError.unreachable
        }
    }

    /// Asked of the server, not the cache, so a second tap never creates a second party.
    private func activeParty(of userId: String, merchantId: String) async throws -> SplitParty? {
        let snapshot = try await Self.openPartiesQuery(firestore, merchantId: merchantId)
            .getDocuments(source: .server)
        let now = Date()
        return snapshot.documents.compactMap(Self.party(from:))
            .filter { $0.isActive(at: now) && $0.isMember(userId) }
            .max { $0.createdAt < $1.createdAt }
    }

    /// Nil when `code` is held by another active party at this merchant.
    private func createParty(hostId: String, merchantId: String, code: JoinCode) async throws -> SplitParty? {
        let lockReference = joinCodes.document(PartySchema.joinCodeLockId(merchantId: merchantId, code: code))
        let partyReference = parties.document()
        // Written by the client (not serverTimestamp) so expiresAt can be exactly
        // createdAt + 5 min in the same write, as the rules demand. Built from the
        // same seconds/nanoseconds so no floating-point rounding creeps in.
        let createdAt = Timestamp(date: Date())
        let expiresAt = Timestamp(seconds: createdAt.seconds + PartySchema.partyLifetimeSeconds,
                                  nanoseconds: createdAt.nanoseconds)
        let partyData: [String: Any] = [
            PartySchema.PartyField.hostId: hostId,
            PartySchema.PartyField.merchantId: merchantId,
            PartySchema.PartyField.joinCode: code.emojis,
            PartySchema.PartyField.status: PartyStatus.open.rawValue,
            PartySchema.PartyField.createdAt: createdAt,
            PartySchema.PartyField.expiresAt: expiresAt,
            PartySchema.PartyField.paidAt: NSNull(),
            PartySchema.PartyField.billCents: NSNull(),
            PartySchema.PartyField.tip: NSNull(),
            PartySchema.PartyField.memberIds: [hostId],
            PartySchema.PartyField.members: [
                hostId: [PartySchema.MemberField.joinedAt: createdAt, PartySchema.MemberField.paid: false],
            ],
            PartySchema.PartyField.wrongCodeAttempts: [String: Any](),
        ]
        let lockData: [String: Any] = [
            PartySchema.LockField.merchantId: merchantId,
            PartySchema.LockField.joinCode: code.emojis,
            PartySchema.LockField.partyId: partyReference.documentID,
            PartySchema.LockField.expiresAt: expiresAt,
        ]

        let result = try await firestore.runTransaction { transaction, errorPointer -> Any? in
            do {
                let lock = try transaction.getDocument(lockReference)
                if let lockExpiry = (lock.get(PartySchema.LockField.expiresAt) as? Timestamp)?.dateValue(),
                   lockExpiry > Date() {
                    return false  // taken by an active party: caller picks another code
                }
                // No lock, or a stale one from an expired party: overwrite it.
                transaction.setData(lockData, forDocument: lockReference)
                transaction.setData(partyData, forDocument: partyReference)
                return true
            } catch {
                errorPointer?.pointee = error as NSError
                return nil
            }
        }
        guard result as? Bool == true else { return nil }
        return SplitParty(id: partyReference.documentID, hostId: hostId, merchantId: merchantId, joinCode: code,
                          status: .open, createdAt: createdAt.dateValue(), expiresAt: expiresAt.dateValue(),
                          memberIds: [hostId], wrongCodeAttempts: [:])
    }

    // MARK: Join

    func joinParty(id: String, userId: String) async throws {
        let partyReference = parties.document(id)
        let joinCodes = self.joinCodes
        try await withPartyTimeout {
            // Firestore retries the block when another join commits first, so both
            // joins end up in memberIds, in commit order.
            let outcome = try await firestore.runTransaction { transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(partyReference)
                    guard let party = Self.party(from: snapshot) else { return PartyActionError.partyNoLongerOpen }
                    let now = Date()
                    if party.isMember(userId) { return nil }
                    if party.isLockedOut(userId) { return PartyActionError.lockedOut }
                    guard party.isActive(at: now), party.memberIds.count < PartySchema.maxMembers else {
                        return PartyActionError.partyNoLongerOpen
                    }
                    // Joining by code (schema): the lock must still point at this party.
                    let lockId = PartySchema.joinCodeLockId(merchantId: party.merchantId, code: party.joinCode)
                    let lock = try transaction.getDocument(joinCodes.document(lockId))
                    guard lock.get(PartySchema.LockField.partyId) as? String == party.id else {
                        return PartyActionError.partyNoLongerOpen
                    }
                    let memberUpdate: [AnyHashable: Any] = [
                        PartySchema.PartyField.memberIds: party.memberIds + [userId],
                        FieldPath([PartySchema.PartyField.members, userId]): [
                            PartySchema.MemberField.joinedAt: Timestamp(date: now),
                            PartySchema.MemberField.paid: false,
                        ],
                    ]
                    transaction.updateData(memberUpdate, forDocument: partyReference)
                    return nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }
            if let refusal = outcome as? PartyActionError { throw refusal }
        }
    }

    // MARK: Wrong code

    func recordWrongCode(partyId: String, userId: String) async throws {
        try await withPartyTimeout {
            try await parties.document(partyId).updateData([
                FieldPath([PartySchema.PartyField.wrongCodeAttempts, userId]): FieldValue.increment(Int64(1)),
            ])
        }
    }

    // MARK: Expire

    func expireParty(id: String) async {
        let partyReference = parties.document(id)
        let joinCodes = self.joinCodes
        // Errors are ignored on purpose: the usual one is that another phone has
        // already expired the party (the rules refuse updates to a non-open party).
        _ = try? await firestore.runTransaction { transaction, errorPointer -> Any? in
            do {
                let snapshot = try transaction.getDocument(partyReference)
                guard let party = Self.party(from: snapshot), party.status == .open,
                      Date() >= party.expiresAt else { return nil }
                let lockReference = joinCodes.document(
                    PartySchema.joinCodeLockId(merchantId: party.merchantId, code: party.joinCode))
                let lock = try transaction.getDocument(lockReference)
                transaction.updateData([PartySchema.PartyField.status: PartyStatus.expired.rawValue],
                                       forDocument: partyReference)
                // A newer party may already hold the same code; never release its lock.
                if lock.get(PartySchema.LockField.partyId) as? String == party.id {
                    transaction.deleteDocument(lockReference)
                }
                return nil
            } catch {
                errorPointer?.pointee = error as NSError
                return nil
            }
        }
    }

    // MARK: Total, tip and members

    func setBill(partyId: String, billCents: Int) async throws {
        try await updateOpenParty(id: partyId) { party in
            var update: [AnyHashable: Any] = [PartySchema.PartyField.billCents: billCents]
            if party.tip == nil {
                update[PartySchema.PartyField.tip] = TipChoice.zero.firestoreValue
            }
            return update
        }
    }

    func setTip(partyId: String, tip: TipChoice) async throws {
        try await updateOpenParty(id: partyId) { _ in
            [PartySchema.PartyField.tip: tip.firestoreValue]
        }
    }

    func removeMember(partyId: String, memberId: String) async throws {
        try await updateOpenParty(id: partyId) { party in
            // The host can never be dropped (the rules also demand memberIds[0] == hostId).
            guard memberId != party.hostId, party.isMember(memberId) else { return nil }
            return [
                PartySchema.PartyField.memberIds: party.memberIds.filter { $0 != memberId },
                FieldPath([PartySchema.PartyField.members, memberId]): FieldValue.delete(),
            ]
        }
    }

    /// One field per write: `members.{uid}.customShareCents`, a number or `null`
    /// to clear (docs/firestore-schema.md "Custom shares", step 3). Removing or
    /// leaving deletes the whole member map, amount included.
    func setCustomShare(partyId: String, memberId: String, cents: Int?) async throws {
        try await updateOpenParty(id: partyId) { party in
            guard party.isMember(memberId) else { return nil }
            _ = try party.customShares(setting: cents, for: memberId)
            let field = FieldPath([PartySchema.PartyField.members, memberId, PartySchema.MemberField.customShareCents])
            guard let cents else { return [field: NSNull()] }
            return [field: cents]
        }
    }

    /// Reads the party in a transaction and applies `makeUpdate` only while it is
    /// open and unexpired. `makeUpdate` may refuse with a `PartyActionError`. A transaction (not a blind update) because the member
    /// list is rewritten whole: Firestore retries the block on top of any join or
    /// leave that commits first, so no two phones overwrite each other. A nil
    /// update means there is nothing to change.
    private func updateOpenParty(id: String,
                                 _ makeUpdate: @escaping @Sendable (SplitParty) throws -> [AnyHashable: Any]?)
        async throws {
        let partyReference = parties.document(id)
        try await withPartyTimeout {
            let outcome = try await firestore.runTransaction { transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(partyReference)
                    guard let party = Self.party(from: snapshot), party.isActive(at: Date()) else {
                        return PartyActionError.partyNoLongerOpen
                    }
                    if let update = try makeUpdate(party) {
                        transaction.updateData(update, forDocument: partyReference)
                    }
                    return nil
                } catch let refusal as PartyActionError {
                    return refusal
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }
            if let refusal = outcome as? PartyActionError { throw refusal }
        }
    }

    // MARK: Pay

    /// docs/firestore-schema.md "Paying a party", as one transaction: either every
    /// payment, balance move and the paid party land together, or nothing does.
    /// The party is read inside the transaction, so a second Pay (double tap,
    /// retry after a dropped connection whose first commit did land) sees `paid`
    /// and writes nothing; the deterministic payment ids are a second guard.
    func settleParty(id: String) async throws {
        do {
            try await runSettlement(id: id)
        } catch {
            // Two racing Pays: the loser's commit is refused by the rules
            // (PERMISSION_DENIED, not retried by the SDK), and a commit whose
            // acknowledgement was lost times out. Either way, if the server says
            // the party is paid, Pay succeeded exactly once.
            if await isPaidOnServer(id: id) { return }
            throw error
        }
    }

    private func isPaidOnServer(id: String) async -> Bool {
        guard let snapshot = try? await parties.document(id).getDocument(source: .server) else { return false }
        return Self.party(from: snapshot)?.status == .paid
    }

    private func runSettlement(id: String) async throws {
        let partyReference = parties.document(id)
        let joinCodes = self.joinCodes
        let merchants = firestore.collection(PartySchema.Collection.merchants)
        let transactions = firestore.collection(Schema.Collection.transactions)
        let users = firestore.collection(Schema.Collection.users)
        try await withPartyTimeout {
            let outcome = try await firestore.runTransaction { transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(partyReference)
                    guard let party = Self.party(from: snapshot) else { return PartyActionError.partyNoLongerOpen }
                    switch party.payBlocker(at: Date()) {
                    case .alreadyPaid: return nil
                    case .expired, .cancelled: return PartyActionError.partyNoLongerOpen
                    case .needsTotal, .needsFriends: return PartyActionError.notReadyToPay
                    case .customSharesDontFit: return party.settleRefusalForCustomShares
                    case nil: break
                    }
                    // Every read before the first write (a Firestore transaction rule).
                    let merchant = try transaction.getDocument(merchants.document(party.merchantId))
                    let lockReference = joinCodes.document(
                        PartySchema.joinCodeLockId(merchantId: party.merchantId, code: party.joinCode))
                    let lock = try transaction.getDocument(lockReference)
                    let merchantName = merchant.get(PartySchema.MerchantField.name) as? String
                        ?? DemoMerchant.fallbackName
                    guard let settlement = PartySettlement(party: party, merchantName: merchantName) else {
                        return PartyActionError.notReadyToPay
                    }
                    for payment in settlement.payments {
                        transaction.setData(Self.transactionData(payment, of: settlement),
                                            forDocument: transactions.document(payment.transactionId))
                        transaction.updateData(
                            [Schema.UserField.balanceCents: FieldValue.increment(Int64(-payment.amountCents))],
                            forDocument: users.document(payment.payerId))
                    }
                    if settlement.collectedCents > 0 {
                        transaction.updateData(
                            [Schema.UserField.balanceCents: FieldValue.increment(Int64(settlement.collectedCents))],
                            forDocument: users.document(settlement.hostId))
                    }
                    transaction.updateData(Self.paidPartyUpdate(party, tip: settlement.tip), forDocument: partyReference)
                    // A paid party no longer holds its code (never release a newer party's lock).
                    if lock.get(PartySchema.LockField.partyId) as? String == party.id {
                        transaction.deleteDocument(lockReference)
                    }
                    return nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }
            if let refusal = outcome as? PartyActionError { throw refusal }
        }
    }

    /// Exactly the fields the rules accept for a new transaction.
    private static func transactionData(_ payment: PartySettlement.Payment,
                                        of settlement: PartySettlement) -> [String: Any] {
        [
            Schema.TransactionField.payerId: payment.payerId,
            Schema.TransactionField.payeeId: settlement.hostId,
            Schema.TransactionField.participantIds: [payment.payerId, settlement.hostId],
            Schema.TransactionField.amountCents: payment.amountCents,
            Schema.TransactionField.memo: settlement.memo,
            Schema.TransactionField.merchantId: settlement.merchantId,
            Schema.TransactionField.merchantName: settlement.merchantName,
            Schema.TransactionField.partyId: settlement.partyId,
            Schema.TransactionField.createdAt: FieldValue.serverTimestamp(),
            Schema.TransactionField.likesCount: 0,
            Schema.TransactionField.commentsCount: 0,
            Schema.TransactionField.source: PartySettlement.transactionSource,
        ]
    }

    /// `status: paid`, `paidAt`, every member's `paid`, and the tip (0% custom
    /// when none was chosen: the rules demand a non-null tip on a paid party).
    private static func paidPartyUpdate(_ party: SplitParty, tip: TipChoice) -> [AnyHashable: Any] {
        var update: [AnyHashable: Any] = [
            PartySchema.PartyField.status: PartyStatus.paid.rawValue,
            PartySchema.PartyField.paidAt: FieldValue.serverTimestamp(),
            PartySchema.PartyField.tip: tip.firestoreValue,
        ]
        for memberId in party.memberIds {
            update[FieldPath([PartySchema.PartyField.members, memberId, PartySchema.MemberField.paid])] = true
        }
        return update
    }

    // MARK: Cancel

    /// docs/firestore-schema.md "Cancelling a party": the party read in the
    /// transaction must be open, unexpired and hosted by `hostId`; then only
    /// status + cancel stamp change and the party's own lock is released.
    func cancelParty(id: String, hostId: String) async throws -> PartyCancellation? {
        do {
            return try await runCancel(id: id, hostId: hostId)
        } catch {
            // A commit whose acknowledgement was lost: if the server shows the party
            // cancelled by this host, the cancel landed (only the host can cancel).
            if let party = await serverParty(id: id), party.isCancelled, party.hostId == hostId {
                return Self.cancellation(of: party, at: party.cancelledAt ?? Date())
            }
            throw error
        }
    }

    private func serverParty(id: String) async -> SplitParty? {
        guard let snapshot = try? await parties.document(id).getDocument(source: .server) else { return nil }
        return Self.party(from: snapshot)
    }

    private static func cancellation(of party: SplitParty, at time: Date) -> PartyCancellation {
        PartyCancellation(partyId: party.id, memberCount: party.memberIds.count,
                          secondsSinceCreated: max(0, Int(time.timeIntervalSince(party.createdAt))))
    }

    private func runCancel(id: String, hostId: String) async throws -> PartyCancellation? {
        let partyReference = parties.document(id)
        let joinCodes = self.joinCodes
        return try await withPartyTimeout {
            let outcome = try await firestore.runTransaction { transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(partyReference)
                    guard let party = Self.party(from: snapshot), party.hostId == hostId else {
                        return PartyActionError.rejected
                    }
                    if party.isCancelled { return NSNull() }  // already done: nothing to write or log
                    let now = Date()
                    guard party.canCancel(by: hostId, at: now) else { return PartyActionError.partyNoLongerOpen }
                    let lockReference = joinCodes.document(
                        PartySchema.joinCodeLockId(merchantId: party.merchantId, code: party.joinCode))
                    let lock = try transaction.getDocument(lockReference)
                    transaction.updateData([
                        PartySchema.PartyField.status: PartyStatus.cancelled.rawValue,
                        PartySchema.PartyField.cancelledAt: FieldValue.serverTimestamp(),
                        PartySchema.PartyField.cancelledBy: hostId,
                    ], forDocument: partyReference)
                    // Freed so the host can start again at once (never a newer party's lock).
                    if lock.get(PartySchema.LockField.partyId) as? String == party.id {
                        transaction.deleteDocument(lockReference)
                    }
                    return CancelOutcome(cancellation: Self.cancellation(of: party, at: now))
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }
            if let refusal = outcome as? PartyActionError { throw refusal }
            return (outcome as? CancelOutcome)?.cancellation
        }
    }

    /// Boxes the value out of the Objective-C transaction block.
    private final class CancelOutcome: NSObject {
        let cancellation: PartyCancellation
        init(cancellation: PartyCancellation) { self.cancellation = cancellation }
    }

    // MARK: Errors

    /// Runs `operation` with the write timeout and maps every failure to a
    /// `PartyActionError` the UI can word.
    private func withPartyTimeout<Value: Sendable>(_ operation: @escaping @Sendable () async throws -> Value)
        async throws -> Value {
        do {
            return try await withThrowingTaskGroup(of: Value.self) { group in
                group.addTask { try await operation() }
                group.addTask {
                    try await Task.sleep(for: Self.writeTimeout)
                    throw PartyActionError.unreachable
                }
                defer { group.cancelAll() }
                guard let first = try await group.next() else { throw PartyActionError.unreachable }
                return first
            }
        } catch let error as PartyActionError {
            throw error
        } catch let error as NSError where error.domain == FirestoreErrorDomain
            && error.code == FirestoreErrorCode.permissionDenied.rawValue {
            throw PartyActionError.rejected
        } catch {
            throw PartyActionError.unreachable
        }
    }
}

/// Adapts a Firestore listener to `LiveSubscription`.
private struct FirestoreListener: LiveSubscription {
    let registration: ListenerRegistration

    func cancel() {
        registration.remove()
    }
}
