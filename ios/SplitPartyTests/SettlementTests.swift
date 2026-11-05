import XCTest
@testable import SplitParty

/// What Pay writes (`PartySettlement`), against the work order's examples and
/// the rules' limits in backend/firestore.rules.
final class SettlementTests: XCTestCase {
    private let createdAt = Date(timeIntervalSince1970: 1_800_000_000)

    private func party(memberIds: [String] = ["annie", "maxi", "christophe"], billCents: Int? = 10_000,
                       tip: TipChoice? = .percent(18), status: PartyStatus = .open) -> SplitParty {
        SplitParty(id: "p1", hostId: memberIds[0], merchantId: DemoMerchant.id,
                   joinCode: JoinCode(["🍕", "🍺", "🎉"])!, status: status, createdAt: createdAt,
                   expiresAt: createdAt.addingTimeInterval(300), memberIds: memberIds, wrongCodeAttempts: [:],
                   billCents: billCents, tip: tip)
    }

    func testAnnieHostsOneHundredAtEighteenPercent() throws {
        let settlement = try XCTUnwrap(PartySettlement(party: party(), merchantName: "Tony's"))
        XCTAssertEqual(settlement.payments, [
            .init(transactionId: "p1_maxi", payerId: "maxi", amountCents: 3_933),
            .init(transactionId: "p1_christophe", payerId: "christophe", amountCents: 3_933),
        ])
        XCTAssertEqual(settlement.collectedCents, 7_866)
        XCTAssertEqual(settlement.memo, "Tony's Split Party")
        XCTAssertEqual(settlement.tip, .percent(18))
    }

    func testNoTipChosenSettlesWithTheZeroCustomTip() throws {
        let settlement = try XCTUnwrap(PartySettlement(party: party(tip: nil), merchantName: "Tony's"))
        XCTAssertEqual(settlement.tip, .custom(amountCents: 0))
        XCTAssertEqual(settlement.tip.firestoreValue as NSDictionary, ["type": "custom", "amountCents": 0])
        // 33.34 stays with the host; only the two joinee shares are paid.
        XCTAssertEqual(settlement.payments.map(\.amountCents), [3_333, 3_333])
        XCTAssertEqual(settlement.collectedCents, 6_666)
    }

    func testAfterARemovalTheOtherFriendPaysHalf() throws {
        let settlement = try XCTUnwrap(PartySettlement(party: party(memberIds: ["annie", "maxi"]),
                                                       merchantName: "Tony's"))
        XCTAssertEqual(settlement.payments, [.init(transactionId: "p1_maxi", payerId: "maxi", amountCents: 5_900)])
    }

    func testZeroSharesAreNotWritten() throws {
        // 2 cents over three people: the host carries both, the friends owe 0.
        let settlement = try XCTUnwrap(PartySettlement(party: party(billCents: 2, tip: nil), merchantName: "Tony's"))
        XCTAssertTrue(settlement.payments.isEmpty)
        XCTAssertEqual(settlement.collectedCents, 0)
    }

    func testMemoNeverExceedsTheRulesLimit() {
        let memo = PartySettlement.memo(merchantName: String(repeating: "🍕", count: 60))
        XCTAssertLessThanOrEqual(memo.utf16.count, PartySettlement.maxMemoLength)
        XCTAssertTrue(memo.hasSuffix(" Split Party"))
        XCTAssertEqual(PartySettlement.memo(merchantName: "  "), "Tony's Split Party")
    }

    func testPayBlockers() {
        let now = createdAt.addingTimeInterval(10)
        XCTAssertNil(party().payBlocker(at: now))
        XCTAssertEqual(party(billCents: nil).payBlocker(at: now), .needsTotal)
        XCTAssertEqual(party(memberIds: ["annie"]).payBlocker(at: now), .needsFriends)
        XCTAssertEqual(party().payBlocker(at: createdAt.addingTimeInterval(300)), .expired)
        XCTAssertEqual(party(status: .paid).payBlocker(at: now), .alreadyPaid)
        XCTAssertEqual(PayBlocker.needsTotal.label, "Add the total")
        XCTAssertEqual(PayBlocker.needsFriends.label, "Waiting for friends to join")
    }

    func testPaidMembersAndPaidAtAreRead() throws {
        let paidAt = createdAt.addingTimeInterval(60)
        let data: [String: Any] = [
            "hostId": "annie", "merchantId": DemoMerchant.id, "joinCode": ["🍕", "🍺", "🎉"], "status": "paid",
            "createdAt": createdAt, "expiresAt": createdAt.addingTimeInterval(300), "paidAt": paidAt,
            "memberIds": ["annie", "maxi"], "wrongCodeAttempts": [:] as [String: Any],
            "billCents": NSNumber(value: Int64(10_000)), "tip": ["type": "custom", "amountCents": 0],
            "members": ["annie": ["joinedAt": createdAt, "paid": true], "maxi": ["joinedAt": createdAt, "paid": true]],
        ]
        let parsed = try XCTUnwrap(SplitParty(id: "p1", data: data, dateOf: { $0 as? Date }))
        XCTAssertEqual(parsed.paidAt, paidAt)
        XCTAssertEqual(parsed.paidMemberIds, ["annie", "maxi"])
    }

    func testDemoResetAnswers() {
        XCTAssertNoThrow(try DemoResetClient.checkResult(data: Data(#"{"result":{"ok":true}}"#.utf8), statusCode: 200))
        XCTAssertThrowsError(try DemoResetClient.checkResult(
            data: Data(#"{"error":{"status":"INTERNAL","message":"partial"}}"#.utf8), statusCode: 500)) {
            XCTAssertEqual($0 as? DemoResetError, .failed(message: "partial"))
        }
        XCTAssertThrowsError(try DemoResetClient.checkResult(data: Data("Not Found".utf8), statusCode: 404)) {
            XCTAssertEqual($0 as? DemoResetError, .unreachable)
        }
        XCTAssertEqual(DemoResetClient(target: .hosted(projectId: "demo"))?.endpoint.absoluteString,
                       "https://us-central1-demo.cloudfunctions.net/resetDemo")
        XCTAssertEqual(DemoResetClient(target: .emulator(projectId: "demo", host: "192.168.1.20:8080"))?
            .endpoint.absoluteString, "http://192.168.1.20:5001/demo/us-central1/resetDemo")
    }
}
