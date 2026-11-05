import XCTest
@testable import SplitParty

/// The party rules this slice relies on: codes come from the palette, the four
/// confirmation options hold exactly one real code, expiry is by the clock, and
/// three wrong attempts lock a user out.
final class JoinCodeTests: XCTestCase {
    private let palette = Set(JoinCode.palette.map(\.emoji))

    func testRandomCodesUseTwoPaletteEmojis() {
        for _ in 0..<500 {
            let code = JoinCode.random()
            XCTAssertEqual(code.emojis.count, 2)
            XCTAssertTrue(code.emojis.allSatisfy(palette.contains))
        }
    }

    func testPaletteMatchesTheSchema() {
        XCTAssertEqual(JoinCode.palette.map(\.emoji).joined(), "🍕🍔🌮🍣🍩🍺🎉🔥🚀🎸🐶🌵")
    }

    func testCodeOutsidePaletteOrWrongLengthIsRejected() {
        XCTAssertNil(JoinCode(["🍕"]))
        XCTAssertNil(JoinCode(["🍕", "🍺", "🎉"]), "A three-emoji code from an older build is rejected")
        XCTAssertNil(JoinCode(["🍕", "😀"]))
        XCTAssertNotNil(JoinCode(["🍕", "🍕"]), "Repeats are allowed")
    }

    func testSpokenNamesReadEveryEmoji() {
        XCTAssertEqual(JoinCode(["🍕", "🍺"])?.spokenNames, "pizza, beer")
    }

    func testChoicesHoldTheRealCodeOnceAndThreeDistinctDecoys() {
        let code = JoinCode(["🍕", "🍺"])!
        for _ in 0..<500 {
            let options = JoinCodeChoices.make(for: code)
            XCTAssertEqual(options.count, 4)
            XCTAssertEqual(options.filter { $0 == code }.count, 1)
            XCTAssertEqual(Set(options).count, 4, "Decoys differ from the code and from each other")
            XCTAssertTrue(options.allSatisfy { $0.emojis.count == 2 && $0.emojis.allSatisfy(palette.contains) })
        }
    }

    func testReshuffleAlwaysChangesTheArrangement() {
        let code = JoinCode(["🔥", "🔥"])!
        var options = JoinCodeChoices.make(for: code)
        for _ in 0..<200 {
            let next = JoinCodeChoices.make(for: code, previous: options)
            XCTAssertNotEqual(next, options)
            options = next
        }
    }

    func testLockIdMatchesTheSchemaFormat() {
        XCTAssertEqual(PartySchema.joinCodeLockId(merchantId: "tonys-greenwich", code: JoinCode(["🍕", "🍺"])!),
                       "tonys-greenwich_🍕🍺")
    }
}

final class SplitPartyDocumentTests: XCTestCase {
    private let createdAt = Date(timeIntervalSince1970: 1_800_000_000)

    private func party(status: PartyStatus = .open, attempts: [String: Int] = [:]) -> SplitParty {
        SplitParty(id: "p1", hostId: "maxi", merchantId: DemoMerchant.id, joinCode: JoinCode(["🍕", "🍺"])!,
                   status: status, createdAt: createdAt, expiresAt: createdAt.addingTimeInterval(300),
                   memberIds: ["maxi", "annie"], wrongCodeAttempts: attempts)
    }

    func testOpenPartyExpiresByTheClockWhateverTheStatusSays() {
        let open = party()
        XCTAssertTrue(open.isActive(at: createdAt.addingTimeInterval(299.9)))
        XCTAssertFalse(open.isActive(at: createdAt.addingTimeInterval(300)))
        XCTAssertTrue(open.isExpired(at: createdAt.addingTimeInterval(300)))
        XCTAssertFalse(party(status: .paid).isExpired(at: createdAt.addingTimeInterval(400)), "Settled, not expired")
        XCTAssertFalse(party(status: .expired).isActive(at: createdAt))
    }

    func testCountdownStartsAtFiveMinutesAndStopsAtZero() {
        XCTAssertEqual(party().secondsLeft(at: createdAt), 300)
        XCTAssertEqual(party().secondsLeft(at: createdAt.addingTimeInterval(299.2)), 1)
        XCTAssertEqual(party().secondsLeft(at: createdAt.addingTimeInterval(500)), 0)
    }

    func testThirdWrongAttemptLocksOut() {
        XCTAssertFalse(party(attempts: ["annie": 2]).isLockedOut("annie"))
        XCTAssertTrue(party(attempts: ["annie": 3]).isLockedOut("annie"))
        XCTAssertFalse(party(attempts: ["annie": 3]).isLockedOut("christophe"))
    }

    func testDocumentMapping() throws {
        let data: [String: Any] = [
            "hostId": "maxi", "merchantId": "tonys-greenwich", "joinCode": ["🍕", "🍺"], "status": "open",
            "createdAt": createdAt, "expiresAt": createdAt.addingTimeInterval(300),
            "memberIds": ["maxi", "annie"], "wrongCodeAttempts": ["christophe": NSNumber(value: Int64(2))],
            "billCents": NSNull(), "tip": NSNull(), "paidAt": NSNull(), "members": [:] as [String: Any],
        ]
        let parsed = try XCTUnwrap(SplitParty(id: "p1", data: data, dateOf: { $0 as? Date }))
        XCTAssertEqual(parsed.memberIds, ["maxi", "annie"])
        XCTAssertEqual(parsed.wrongAttempts(by: "christophe"), 2)
        XCTAssertNil(SplitParty(id: "bad", data: data.merging(["joinCode": ["🍕"]]) { $1 }, dateOf: { $0 as? Date }))
        XCTAssertNil(SplitParty(id: "older-build", data: data.merging(["joinCode": ["🍕", "🍺", "🎉"]]) { $1 },
                                dateOf: { $0 as? Date }), "A party with a three-emoji code is skipped, not a crash")
    }
}
