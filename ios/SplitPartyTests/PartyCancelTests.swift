import XCTest
@testable import SplitParty

/// Host cancel (PRD US-12/13, FR-2.8–2.10): `cancelled` is its own state, only
/// the host cancels an open party, nothing is charged, and one `party_cancelled`
/// event is recorded per cancel.
@MainActor
final class PartyCancelTests: XCTestCase {
    private let createdAt = Date(timeIntervalSince1970: 1_800_000_000)

    override func tearDown() {
        Analytics.observer = nil
        super.tearDown()
    }

    // MARK: Contract

    func testCancelledIsParsedAsCancelledNotExpired() throws {
        let data: [String: Any] = [
            "hostId": "maxi", "merchantId": "tonys-greenwich", "joinCode": ["🍕", "🍺"], "status": "cancelled",
            "createdAt": createdAt, "expiresAt": createdAt.addingTimeInterval(300),
            "cancelledAt": createdAt.addingTimeInterval(120), "cancelledBy": "maxi",
            "memberIds": ["maxi", "annie"], "wrongCodeAttempts": [:] as [String: Any],
            "billCents": NSNull(), "tip": NSNull(), "paidAt": NSNull(), "members": [:] as [String: Any],
        ]
        let party = try XCTUnwrap(SplitParty(id: "p1", data: data, dateOf: { $0 as? Date }))
        XCTAssertEqual(party.status, .cancelled)
        XCTAssertTrue(party.isCancelled)
        XCTAssertEqual(party.cancelledAt, createdAt.addingTimeInterval(120))
        XCTAssertFalse(party.isExpired(at: createdAt.addingTimeInterval(400)), "Cancelled is never shown as expired")
        XCTAssertFalse(party.isActive(at: createdAt.addingTimeInterval(10)))
        XCTAssertEqual(party.payBlocker(at: createdAt.addingTimeInterval(10)), .cancelled)
    }

    func testOnlyTheHostOfAnOpenUnexpiredPartyMayCancel() {
        let party = open(memberIds: ["maxi", "annie"])
        XCTAssertTrue(party.canCancel(by: "maxi", at: createdAt.addingTimeInterval(299)))
        XCTAssertFalse(party.canCancel(by: "annie", at: createdAt))
        XCTAssertFalse(party.canCancel(by: "maxi", at: createdAt.addingTimeInterval(300)), "Expired by the clock")
    }

    // MARK: Writes (the fixture follows PartyRepository's rules)

    func testHostCancelsWithNoTotalAndNoJoineesAndTheCodeIsFree() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi"])
        let cancellation = try await fixture.cancelParty(id: party.id, hostId: "maxi")
        XCTAssertEqual(cancellation?.partyId, party.id)
        XCTAssertEqual(cancellation?.memberCount, 1)
        XCTAssertEqual(try current(party.id, in: fixture).status, .cancelled)
        XCTAssertTrue(openParties(in: fixture).isEmpty, "Gone from Home's banner slot")
        let next = try await fixture.startParty(hostId: "maxi", merchantId: DemoMerchant.id)
        XCTAssertNotEqual(next.id, party.id, "The host starts a new party straight away")
    }

    func testANonHostCannotCancel() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"])
        await assertThrows(.rejected) { _ = try await fixture.cancelParty(id: party.id, hostId: "annie") }
        XCTAssertEqual(try current(party.id, in: fixture).status, .open)
    }

    func testExpiredOrPaidPartiesCannotBeCancelled() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let stale = fixture.addParty(hostId: "maxi", memberIds: ["maxi"], createdAt: Date().addingTimeInterval(-301))
        await assertThrows(.partyNoLongerOpen) { _ = try await fixture.cancelParty(id: stale.id, hostId: "maxi") }
        XCTAssertEqual(try current(stale.id, in: fixture).status, .open)

        let paid = fixture.addParty(hostId: "annie", memberIds: ["annie", "maxi"], billCents: 1_000, tip: .zero)
        try await fixture.settleParty(id: paid.id)
        await assertThrows(.partyNoLongerOpen) { _ = try await fixture.cancelParty(id: paid.id, hostId: "annie") }
        XCTAssertEqual(try current(paid.id, in: fixture).status, .paid)
    }

    func testACancelledPartyCanNeitherBeJoinedNorPaid() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"], billCents: 4_000, tip: .zero)
        _ = try await fixture.cancelParty(id: party.id, hostId: "maxi")
        await assertThrows(.partyNoLongerOpen) { try await fixture.joinParty(id: party.id, userId: "christophe") }
        await assertThrows(.partyNoLongerOpen) { try await fixture.settleParty(id: party.id) }
        XCTAssertEqual(try current(party.id, in: fixture).memberIds, ["maxi", "annie"])
        let second = try await fixture.cancelParty(id: party.id, hostId: "maxi")
        XCTAssertNil(second, "A second cancel writes nothing")
    }

    // MARK: Analytics

    func testTheHostsCancelRecordsPartyCancelledOnce() async throws {
        var events: [AnalyticsEvent] = []
        Analytics.observer = { events.append($0) }
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"],
                                     createdAt: Date().addingTimeInterval(-90))
        let room = PartyRoomModel(partyId: party.id, userId: "maxi", repository: fixture,
                                  connection: ConnectionMonitor())
        defer { room.stop() }
        let error = await room.cancel()
        XCTAssertNil(error)
        _ = await room.cancel()
        XCTAssertEqual(events.count, 1)
        XCTAssertEqual(events.first?.name, "party_cancelled")
        XCTAssertEqual(events.first?.parameters["party_id"], party.id)
        XCTAssertEqual(events.first?.parameters["member_count"], "3")
        let seconds = try XCTUnwrap(events.first?.parameters["seconds_since_created"].flatMap(Int.init))
        XCTAssertTrue((89...95).contains(seconds), "\(seconds)")
    }

    func testAJoineeCannotCancelFromTheScreenModel() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"])
        let room = PartyRoomModel(partyId: party.id, userId: "annie", repository: fixture,
                                  connection: ConnectionMonitor())
        defer { room.stop() }
        XCTAssertFalse(room.isHost)
        _ = await room.cancel()
        XCTAssertEqual(try current(party.id, in: fixture).status, .open)
    }

    // MARK: Helpers

    private func open(memberIds: [String]) -> SplitParty {
        SplitParty(id: "p1", hostId: "maxi", merchantId: DemoMerchant.id, joinCode: JoinCode(["🍕", "🍺"])!,
                   status: .open, createdAt: createdAt, expiresAt: createdAt.addingTimeInterval(300),
                   memberIds: memberIds, wrongCodeAttempts: [:])
    }

    private func current(_ partyId: String, in fixture: FixtureDataSource) throws -> SplitParty {
        var latest: SplitParty?
        fixture.listenToParty(id: partyId) { latest = try? $0.get().item }.cancel()
        return try XCTUnwrap(latest)
    }

    private func openParties(in fixture: FixtureDataSource) -> [SplitParty] {
        var latest: [SplitParty] = []
        fixture.listenToOpenParties(merchantId: DemoMerchant.id) { latest = (try? $0.get().items) ?? [] }.cancel()
        return latest
    }

    private func assertThrows(_ expected: PartyActionError, _ operation: () async throws -> Void,
                              file: StaticString = #filePath, line: UInt = #line) async {
        do {
            try await operation()
            XCTFail("Expected \(expected)", file: file, line: line)
        } catch {
            XCTAssertEqual(error as? PartyActionError, expected, file: file, line: line)
        }
    }
}
