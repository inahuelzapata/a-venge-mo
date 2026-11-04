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
