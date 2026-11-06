import XCTest
@testable import SplitParty

/// The iOS port of backend/lib/split.js must give the same cents as the
/// reference (and therefore as Android) for the work order's worked examples.
final class SplitRuleTests: XCTestCase {
    private let threeMembers = ["maxi", "annie", "christophe"]

    func testEighteenPercentOnOneHundredForThree() throws {
        let split = try XCTUnwrap(PartySplit(billCents: 10_000, tip: .percent(18), memberIds: threeMembers))
        XCTAssertEqual(split.tipCents, 1_800)
        XCTAssertEqual(split.totalCents, 11_800)
        XCTAssertEqual(split.share(of: "maxi"), 3_934)
        XCTAssertEqual(split.share(of: "annie"), 3_933)
        XCTAssertEqual(split.share(of: "christophe"), 3_933)
    }

    func testCustomSevenDollarsOnOneHundredForThree() throws {
        let split = try XCTUnwrap(PartySplit(billCents: 10_000, tip: .custom(amountCents: 700),
                                             memberIds: threeMembers))
        XCTAssertEqual(split.totalCents, 10_700)
        XCTAssertEqual(split.memberIds.map { split.share(of: $0) }, [3_568, 3_566, 3_566])
    }

    func testPercentTipRoundsHalfUp() throws {
        // 3333 × 15 = 49 995 → 499.95 cents → 500.
        let split = try XCTUnwrap(PartySplit(billCents: 3_333, tip: .percent(15), memberIds: ["maxi"]))
        XCTAssertEqual(split.tipCents, 500)
        XCTAssertEqual(split.totalCents, 3_833)
    }

    func testMissingTipCountsAsZeroAndNoBillMeansNoSplit() {
        XCTAssertEqual(PartySplit(billCents: 4_850, tip: nil, memberIds: ["maxi"])?.totalCents, 4_850)
        XCTAssertNil(PartySplit(billCents: nil, tip: .percent(18), memberIds: threeMembers))
        XCTAssertNil(PartySplit(billCents: 100, tip: nil, memberIds: []))
    }

    func testSharesAlwaysSumToTheTotalWithLeftoverOnTheHost() {
        for bill in stride(from: 1, through: 5_000, by: 37) {
            for headcount in 1...8 {
                let members = (0..<headcount).map { "m\($0)" }
                for tip in [TipChoice.zero, .percent(15), .percent(18), .percent(20), .percent(25),
                            .custom(amountCents: 333)] {
                    let split = PartySplit(billCents: bill, tip: tip, memberIds: members)!
                    let shares = members.map { split.share(of: $0)! }
                    XCTAssertEqual(shares.reduce(0, +), split.totalCents)
                    XCTAssertTrue(shares.dropFirst().allSatisfy { $0 == shares.last })
                    XCTAssertLessThan(shares[0] - shares.last!, headcount)
                }
            }
        }
    }
}

/// The extended split rule: custom amounts pay exactly that, the rest of the
/// total is split equally among the others, leftover cents to the first of them
/// in join order (the host when the host is on the remainder).
final class CustomShareRuleTests: XCTestCase {
    private let threeMembers = ["maxi", "annie", "christophe"]

    private func split(totalCents: Int, customs: [String: Int],
                       members: [String]? = nil) throws -> PartySplit {
        try XCTUnwrap(PartySplit(billCents: totalCents, tip: .zero, memberIds: members ?? threeMembers,
                                 customShareCentsByMember: customs))
    }

    func testNoCustomAmountsIsTheUnchangedEqualSplit() throws {
        let extended = try XCTUnwrap(PartySplit(billCents: 10_000, tip: .percent(18), memberIds: threeMembers,
                                                customShareCentsByMember: [:]))
        let equal = try XCTUnwrap(PartySplit(billCents: 10_000, tip: .percent(18), memberIds: threeMembers))
        XCTAssertEqual(extended.sharesByMember, equal.sharesByMember)
        XCTAssertEqual(threeMembers.map { extended.share(of: $0) }, [3_934, 3_933, 3_933])
        XCTAssertNil(extended.customShareProblem)
        XCTAssertFalse(extended.hasCustomShares)
    }

    func testSixtyDollarsTenThirtyFiveAndTheRest() throws {
        let split = try split(totalCents: 6_000, customs: ["maxi": 1_000, "annie": 3_500])
        XCTAssertEqual(threeMembers.map { split.share(of: $0) }, [1_000, 3_500, 1_500])
        XCTAssertTrue(split.hasCustomShare("maxi"))
        XCTAssertTrue(split.hasCustomShare("annie"))
        XCTAssertFalse(split.hasCustomShare("christophe"))
        XCTAssertNil(split.customShareProblem)
    }

    func testClearingOneAmountSplitsTheRestEqually() throws {
        let split = try split(totalCents: 6_000, customs: ["maxi": 1_000])
        XCTAssertEqual(threeMembers.map { split.share(of: $0) }, [1_000, 2_500, 2_500])
    }

    func testLeftoverCentsGoToTheHostWhenOnTheRemainder() throws {
        // 10 001 − 1 000 = 9 001 over two: 4 501 for the host, 4 500 for Christophe.
        let split = try split(totalCents: 10_001, customs: ["annie": 1_000])
        XCTAssertEqual(threeMembers.map { split.share(of: $0) }, [4_501, 1_000, 4_500])
    }

    func testLeftoverCentsGoToTheFirstJoineeOnTheRemainderWhenTheHostIsCustom() throws {
        let members = ["maxi", "annie", "christophe", "dana"]
        // 10 002 − 1 000 = 9 002 over three: base 3 000, 2 leftover cents to Annie.
        let split = try split(totalCents: 10_002, customs: ["maxi": 1_000], members: members)
        XCTAssertEqual(members.map { split.share(of: $0) }, [1_000, 3_002, 3_000, 3_000])
    }

    func testEveryoneCustomSummingExactlyToTheTotalIsValid() throws {
        let split = try split(totalCents: 6_000, customs: ["maxi": 1_000, "annie": 3_500, "christophe": 1_500])
        XCTAssertNil(split.customShareProblem)
        XCTAssertEqual(threeMembers.map { split.share(of: $0) }, [1_000, 3_500, 1_500])
    }

    func testEveryoneCustomNotSummingToTheTotalIsRejected() throws {
        let split = try split(totalCents: 6_000, customs: ["maxi": 1_000, "annie": 3_500, "christophe": 1_000])
        XCTAssertEqual(split.customShareProblem,
                       .everyoneCustomButNotTheTotal(customCents: 5_500, totalCents: 6_000))
    }

    func testOverTheTotalIsRejectedAndTheRestShowZero() throws {
        let split = try split(totalCents: 6_000, customs: ["annie": 3_500, "christophe": 3_500])
        XCTAssertEqual(split.customShareProblem, .exceedsTotal(customCents: 7_000, totalCents: 6_000))
        XCTAssertEqual(split.share(of: "maxi"), 0)
        XCTAssertEqual(split.customShareProblem?.message,
                       "The custom amounts add up to $70.00, more than the total of $60.00.")
    }

    func testLoweringTheBillUnderTheCustomAmountsBecomesInvalid() throws {
        XCTAssertNil(try split(totalCents: 6_000, customs: ["annie": 3_500]).customShareProblem)
        XCTAssertEqual(try split(totalCents: 3_000, customs: ["annie": 3_500]).customShareProblem,
                       .exceedsTotal(customCents: 3_500, totalCents: 3_000))
    }

    func testADepartedMembersAmountIsIgnored() throws {
        let split = try split(totalCents: 6_000, customs: ["maxi": 1_000, "annie": 3_500],
                              members: ["maxi", "christophe"])
        XCTAssertEqual(split.share(of: "maxi"), 1_000)
        XCTAssertEqual(split.share(of: "christophe"), 5_000)
        XCTAssertNil(split.share(of: "annie"))
        XCTAssertFalse(split.hasCustomShare("annie"))
    }

    func testZeroCustomAmountIsValidAndNegativeIsNot() throws {
        XCTAssertEqual(try split(totalCents: 6_000, customs: ["annie": 0]).share(of: "christophe"), 3_000)
        XCTAssertNil(try split(totalCents: 6_000, customs: ["annie": 0]).customShareProblem)
        XCTAssertEqual(CustomShareProblem.check(customShareCentsByMember: ["annie": -1], memberIds: threeMembers,
                                                totalCents: 6_000), .negativeAmount)
    }

    func testValidSharesAlwaysSumToTheTotal() throws {
        for total in stride(from: 0, through: 3_000, by: 97) {
            for headcount in 2...8 {
                let members = (0..<headcount).map { "m\($0)" }
                for customCount in 0..<headcount {
                    let customs = Dictionary(uniqueKeysWithValues: members.prefix(customCount).enumerated()
                        .map { ($1, ($0 * 37) % (total / headcount + 1)) })
                    let split = try split(totalCents: total, customs: customs, members: members)
                    guard split.customShareProblem == nil else { continue }
                    XCTAssertEqual(members.compactMap { split.share(of: $0) }.reduce(0, +), total)
                }
            }
        }
    }

    func testCustomShareKeypadRefusesZero() {
        XCTAssertEqual(CentsInput.customShare.parse("0"), .failure(.belowMinimum(1)))
        XCTAssertEqual(CentsInput.customShare.parse("0.01"), .success(1))
        XCTAssertEqual(CentsInput.customShare.parse("9999.99"), .success(999_999))
        XCTAssertEqual(CentsInput.customShare.parse("10000"), .failure(.aboveMaximum(999_999)))
        XCTAssertEqual(CentsInput.customShare.parse("35.001"), .failure(.tooManyDecimals))
    }
}

final class TipChoiceTests: XCTestCase {
    func testFirestoreShapesRoundTrip() {
        for tip in [TipChoice.percent(18), .custom(amountCents: 700), .zero] {
            XCTAssertEqual(TipChoice(firestoreValue: tip.firestoreValue), tip)
        }
        XCTAssertEqual(TipChoice.percent(18).firestoreValue as NSDictionary, ["type": "percent", "percent": 18])
        XCTAssertEqual(TipChoice.zero.firestoreValue as NSDictionary, ["type": "custom", "amountCents": 0])
    }

    func testShapesTheRulesRejectAreIgnored() {
        XCTAssertNil(TipChoice(firestoreValue: nil))
        XCTAssertNil(TipChoice(firestoreValue: NSNull()))
        XCTAssertNil(TipChoice(firestoreValue: ["type": "percent", "percent": 0]))
        XCTAssertNil(TipChoice(firestoreValue: ["type": "custom", "amountCents": -5]))
        XCTAssertNil(TipChoice(firestoreValue: ["type": "custom", "amountCents": 7.5]))
        XCTAssertNil(TipChoice(firestoreValue: ["type": "other"]))
    }

    func testPartyDocumentCarriesBillAndTip() throws {
        let createdAt = Date(timeIntervalSince1970: 1_800_000_000)
        let data: [String: Any] = [
            "hostId": "maxi", "merchantId": DemoMerchant.id, "joinCode": ["🍕", "🍺"], "status": "open",
            "createdAt": createdAt, "expiresAt": createdAt.addingTimeInterval(300),
            "memberIds": ["maxi", "annie", "christophe"], "billCents": 10_000,
            "tip": ["type": "percent", "percent": 18],
        ]
        let party = try XCTUnwrap(SplitParty(id: "p1", data: data, dateOf: { $0 as? Date }))
        XCTAssertEqual(party.billCents, 10_000)
        XCTAssertEqual(party.tip, .percent(18))
        XCTAssertEqual(party.split?.share(of: "maxi"), 3_934)

        var unset = data
        unset["billCents"] = NSNull()
        unset["tip"] = NSNull()
        let waiting = try XCTUnwrap(SplitParty(id: "p1", data: unset, dateOf: { $0 as? Date }))
        XCTAssertNil(waiting.billCents)
        XCTAssertNil(waiting.split)
    }

    /// `members.{uid}.customShareCents` as written by any client: an amount, null,
    /// absent (older documents) or malformed (ignored, so the member is on the equal split).
    func testPartyDocumentCarriesCustomShares() throws {
        let createdAt = Date(timeIntervalSince1970: 1_800_000_000)
        let joined: [String: Any] = ["joinedAt": createdAt, "paid": false]
        func member(_ extra: [String: Any]) -> [String: Any] { joined.merging(extra) { $1 } }
        let data: [String: Any] = [
            "hostId": "maxi", "merchantId": DemoMerchant.id, "joinCode": ["🍕", "🍺"], "status": "open",
            "createdAt": createdAt, "expiresAt": createdAt.addingTimeInterval(300),
            "memberIds": ["maxi", "annie", "christophe"], "billCents": 6_000,
            "tip": ["type": "custom", "amountCents": 0],
            "members": [
                "maxi": member(["customShareCents": 1_000]),
                "annie": member(["customShareCents": NSNumber(value: 3_500)]),
                "christophe": member(["customShareCents": NSNull()]),
            ],
        ]
        let party = try XCTUnwrap(SplitParty(id: "p1", data: data, dateOf: { $0 as? Date }))
        XCTAssertEqual(party.customShareCentsByMember, ["maxi": 1_000, "annie": 3_500])
        XCTAssertEqual(party.split.map { s in party.memberIds.map { s.share(of: $0) } }, [1_000, 3_500, 1_500])

        var legacy = data
        legacy["members"] = ["maxi": joined, "annie": joined, "christophe": joined]
        let plain = try XCTUnwrap(SplitParty(id: "p1", data: legacy, dateOf: { $0 as? Date }))
        XCTAssertTrue(plain.customShareCentsByMember.isEmpty)
        XCTAssertEqual(plain.split?.share(of: "christophe"), 2_000)

        var malformed = data
        malformed["members"] = ["maxi": member(["customShareCents": 10.5]),
                                "annie": member(["customShareCents": -5]),
                                "christophe": member(["customShareCents": "35"])]
        XCTAssertTrue(try XCTUnwrap(SplitParty(id: "p1", data: malformed, dateOf: { $0 as? Date }))
            .customShareCentsByMember.isEmpty)
    }
}

final class CentsInputTests: XCTestCase {
    func testBillAcceptsTheRangeEnds() {
        XCTAssertEqual(try CentsInput.bill.parse("0.01").get(), 1)
        XCTAssertEqual(try CentsInput.bill.parse("9999.99").get(), 999_999)
        XCTAssertEqual(try CentsInput.bill.parse("48.50").get(), 4_850)
        XCTAssertEqual(try CentsInput.bill.parse("48.5").get(), 4_850)
        XCTAssertEqual(try CentsInput.bill.parse("48").get(), 4_800)
        XCTAssertEqual(try CentsInput.bill.parse("48,50").get(), 4_850, "Decimal comma from some keypads")
        XCTAssertEqual(try CentsInput.bill.parse("$.75").get(), 75)
    }

    func testBillRejectsWhatTheWorkOrderBlocks() {
        XCTAssertEqual(CentsInput.bill.parse("0"), .failure(.belowMinimum(1)))
        XCTAssertEqual(CentsInput.bill.parse("10000.00"), .failure(.aboveMaximum(999_999)))
        XCTAssertEqual(CentsInput.bill.parse("12.345"), .failure(.tooManyDecimals))
        XCTAssertEqual(CentsInput.bill.parse(""), .failure(.empty))
        XCTAssertEqual(CentsInput.bill.parse("1.2.3"), .failure(.notANumber))
        XCTAssertEqual(CentsInput.bill.parse("."), .failure(.notANumber))
        XCTAssertEqual(CentsInput.bill.parse("-5"), .failure(.notANumber))
        XCTAssertEqual(CentsInput.bill.parse("99999999999999999999"), .failure(.aboveMaximum(999_999)))
    }

    func testCustomTipMayBeZero() {
        XCTAssertEqual(try CentsInput.customTip.parse("0").get(), 0)
        XCTAssertEqual(try CentsInput.customTip.parse("7.00").get(), 700)
    }

    func testEditableTextAndSpokenAmounts() {
        XCTAssertEqual(CentsInput.editableText(cents: 4_850), "48.50")
        XCTAssertEqual(CentsInput.editableText(cents: 5), "0.05")
        XCTAssertEqual(Money.spoken(cents: 3_934), "39 dollars and 34 cents")
        XCTAssertEqual(Money.spoken(cents: 101), "1 dollar and 1 cent")
        XCTAssertEqual(Money.spoken(cents: 11_800), "118 dollars")
    }
}

/// The write rules the fixture shares with PartyRepository: a bill always lands
/// with a tip state, the host is never dropped, and nothing is written after expiry.
@MainActor
final class PartyWriteTests: XCTestCase {
    func testFirstBillCarriesTheZeroTipAndLaterBillsKeepTheChosenTip() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"])
        try await fixture.setBill(partyId: party.id, billCents: 4_850)
        XCTAssertEqual(try current(party.id, in: fixture).tip, .zero)
        try await fixture.setTip(partyId: party.id, tip: .percent(18))
        try await fixture.setBill(partyId: party.id, billCents: 20_000)
        let updated = try current(party.id, in: fixture)
        XCTAssertEqual(updated.billCents, 20_000)
        XCTAssertEqual(updated.tip, .percent(18))
    }

    func testRemovingKeepsTheHostAndOtherMembers() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"])
        try await fixture.removeMember(partyId: party.id, memberId: "maxi")
        try await fixture.removeMember(partyId: party.id, memberId: "christophe")
        XCTAssertEqual(try current(party.id, in: fixture).memberIds, ["maxi", "annie"])
    }

    func testNothingIsWrittenAfterExpiry() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi"], createdAt: Date().addingTimeInterval(-301),
                                     billCents: 1_000, tip: .percent(15))
        do {
            try await fixture.setBill(partyId: party.id, billCents: 2_000)
            XCTFail("An expired party must refuse a new bill")
        } catch {
            XCTAssertEqual(error as? PartyActionError, .partyNoLongerOpen)
        }
        XCTAssertEqual(try current(party.id, in: fixture).billCents, 1_000)
    }

    func testHostSetsTenAndThirtyFiveAndTheRestIsTheRemainder() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"], billCents: 6_000,
                                     tip: .zero)
        try await fixture.setCustomShare(partyId: party.id, memberId: "maxi", cents: 1_000)
        try await fixture.setCustomShare(partyId: party.id, memberId: "annie", cents: 3_500)
        let split = try XCTUnwrap(try current(party.id, in: fixture).split)
        XCTAssertEqual(["maxi", "annie", "christophe"].map { split.share(of: $0) }, [1_000, 3_500, 1_500])
    }

    func testAnAmountThatWouldExceedTheTotalIsRefusedAndTheLastValidStateKept() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"], billCents: 6_000,
                                     tip: .zero, customShareCentsByMember: ["annie": 3_500])
        do {
            try await fixture.setCustomShare(partyId: party.id, memberId: "christophe", cents: 3_500)
            XCTFail("$70 of custom amounts must not fit a $60 total")
        } catch {
            XCTAssertEqual(error as? PartyActionError,
                           .customSharesDontFit(.exceedsTotal(customCents: 7_000, totalCents: 6_000)))
        }
        XCTAssertEqual(try current(party.id, in: fixture).customShareCentsByMember, ["annie": 3_500])
    }

    func testEveryoneCustomMustAddUpToTheTotal() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"], billCents: 6_000, tip: .zero,
                                     customShareCentsByMember: ["maxi": 1_000])
        do {
            try await fixture.setCustomShare(partyId: party.id, memberId: "annie", cents: 4_000)
            XCTFail("Everyone custom at $50 must not settle a $60 total")
        } catch {
            XCTAssertEqual(error as? PartyActionError,
                           .customSharesDontFit(.everyoneCustomButNotTheTotal(customCents: 5_000, totalCents: 6_000)))
        }
        try await fixture.setCustomShare(partyId: party.id, memberId: "annie", cents: 5_000)
        XCTAssertNil(try current(party.id, in: fixture).split?.customShareProblem)
    }

    func testClearingReturnsTheMemberToTheEqualSplit() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"], billCents: 6_000,
                                     tip: .zero, customShareCentsByMember: ["maxi": 1_000, "annie": 3_500])
        try await fixture.setCustomShare(partyId: party.id, memberId: "annie", cents: nil)
        let split = try XCTUnwrap(try current(party.id, in: fixture).split)
        XCTAssertEqual(["maxi", "annie", "christophe"].map { split.share(of: $0) }, [1_000, 2_500, 2_500])
        XCTAssertFalse(split.hasCustomShare("annie"))
    }

    func testLoweringTheBillUnderTheAmountsBlocksCollectUntilFixed() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"], billCents: 6_000,
                                     tip: .zero, customShareCentsByMember: ["maxi": 1_000, "annie": 3_500])
        try await fixture.setBill(partyId: party.id, billCents: 4_000)
        var lowered = try current(party.id, in: fixture)
        XCTAssertEqual(lowered.payBlocker(at: Date()), .customSharesDontFit)
        XCTAssertEqual(PayBlocker.customSharesDontFit.label, "Fix the custom amounts")
        do {
            try await fixture.settleParty(id: party.id)
            XCTFail("Collect must refuse amounts that no longer fit")
        } catch {
            XCTAssertEqual(error as? PartyActionError,
                           .customSharesDontFit(.exceedsTotal(customCents: 4_500, totalCents: 4_000)))
        }
        try await fixture.setCustomShare(partyId: party.id, memberId: "annie", cents: nil)
        lowered = try current(party.id, in: fixture)
        XCTAssertNil(lowered.payBlocker(at: Date()))
        XCTAssertEqual(lowered.split?.share(of: "annie"), 1_500)
    }

    func testALeavingMemberTakesTheirAmountWithThem() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let party = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie", "christophe"], billCents: 6_000,
                                     tip: .zero, customShareCentsByMember: ["maxi": 1_000, "annie": 3_500])
        try await fixture.removeMember(partyId: party.id, memberId: "annie")
        let left = try current(party.id, in: fixture)
        XCTAssertEqual(left.customShareCentsByMember, ["maxi": 1_000])
        XCTAssertEqual(left.split?.share(of: "christophe"), 5_000)
        XCTAssertEqual(left.split.map { s in left.memberIds.compactMap { s.share(of: $0) }.reduce(0, +) }, 6_000)
    }

    func testNoCustomAmountAfterExpiryButAnyAmountBeforeATotal() async throws {
        let fixture = FixtureDataSource(scenario: .seeded)
        let expired = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"],
                                       createdAt: Date().addingTimeInterval(-301), billCents: 6_000, tip: .zero)
        do {
            try await fixture.setCustomShare(partyId: expired.id, memberId: "annie", cents: 1_000)
            XCTFail("An expired party must refuse a custom amount")
        } catch {
            XCTAssertEqual(error as? PartyActionError, .partyNoLongerOpen)
        }
        // Without a bill there is no total to exceed (schema "Custom shares", rules packedFits).
        let noBill = fixture.addParty(hostId: "maxi", memberIds: ["maxi", "annie"])
        try await fixture.setCustomShare(partyId: noBill.id, memberId: "annie", cents: 1_000)
        XCTAssertEqual(try current(noBill.id, in: fixture).customShareCentsByMember, ["annie": 1_000])
    }

    private func current(_ partyId: String, in fixture: FixtureDataSource) throws -> SplitParty {
        var latest: SplitParty?
        fixture.listenToParty(id: partyId) { latest = try? $0.get().item }.cancel()
        return try XCTUnwrap(latest)
    }
}

/// The scan's total-line choice, on recognizer output shaped like real receipts
/// (label and amount often come back as separate pieces on the same line).
final class ReceiptTotalDetectorTests: XCTestCase {
    /// Builds pieces top to bottom; each row is one printed line split into
    /// left-hand and right-hand pieces, like the recognizer returns them.
    private func receipt(_ rows: [(String, String?)]) -> [RecognizedText] {
        let lineHeight = 0.03
        return rows.enumerated().flatMap { index, row -> [RecognizedText] in
            let y = 0.9 - Double(index) * 0.05
            var pieces = [RecognizedText(text: row.0, box: CGRect(x: 0.1, y: y, width: 0.4, height: lineHeight))]
            if let right = row.1 {
                pieces.append(RecognizedText(text: right, box: CGRect(x: 0.7, y: y + 0.004, width: 0.2,
                                                                      height: lineHeight)))
            }
            return pieces
        }
    }

    func testPicksTheTotalOverSubtotalTaxAndTipLines() {
        let pieces = receipt([
            ("TONY'S", nil), ("Greenwich Ave, Greenwich CT", nil),
            ("2 Margherita", "$36.00"), ("1 Caesar Salad", "$12.50"),
            ("Subtotal", "$48.50"), ("Tax 6.35%", "$3.08"), ("TOTAL", "$51.58"),
            ("Suggested tip 18%", "$8.73"), ("Total with 18% tip", "$60.31"),
        ])
        XCTAssertEqual(ReceiptTotalDetector.totalCents(in: pieces), 5_158)
    }

    func testAmountDueWinsOverAPlainTotalAndAmountMayBeOnTheNextLine() {
        XCTAssertEqual(ReceiptTotalDetector.totalCents(in: receipt([
            ("Total", "$40.00"), ("Amount due", "$42.10"),
        ])), 4_210)
        XCTAssertEqual(ReceiptTotalDetector.totalCents(in: receipt([
            ("Sub-total", "20.00"), ("TOTAL", nil), ("21.27", nil),
        ])), 2_127)
    }

    func testNoTotalOnABlankOrUnreadablePage() {
        XCTAssertNil(ReceiptTotalDetector.totalCents(in: []))
        XCTAssertNil(ReceiptTotalDetector.totalCents(in: receipt([("Subtotal", "$48.50"), ("Tax", "$3.08")])))
        XCTAssertNil(ReceiptTotalDetector.totalCents(in: receipt([("TOTAL", "5l.S8")])), "Garbled digits")
    }

    func testAmountFormats() {
        XCTAssertEqual(ReceiptTotalDetector.amounts(in: "TOTAL $1,234.56"), [123_456])
        XCTAssertEqual(ReceiptTotalDetector.amounts(in: "Total 48,50"), [4_850])
        XCTAssertEqual(ReceiptTotalDetector.amounts(in: "Table 12 Guests 3"), [])
        XCTAssertEqual(ReceiptTotalDetector.amounts(in: "Total 12345.00"), [], "Above $9,999.99")
    }
}
