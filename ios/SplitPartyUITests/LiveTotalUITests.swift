import XCTest

/// Live check of the total slice against the configured Firestore project (no
/// fixture): two phones, run at the same time on two simulators. Skipped unless
/// `LIVE_PARTY_ROLE` is `host` or `member` and `LIVE_FIRESTORE_PROJECT` is set
/// (pass them as `TEST_RUNNER_LIVE_PARTY_ROLE` / `TEST_RUNNER_LIVE_FIRESTORE_PROJECT`).
///
/// Host (Maxi) starts a party, enters 100.00, picks 18%, removes Annie once she
/// joins, changes the tip to 20% when she is back, and sees her leave. Member
/// (Annie) joins by the code read from the party document, gets removed, rejoins
/// and leaves. Each phone checks its own share on screen; the party document is
/// read over Firestore's REST API (the rules allow public reads) to confirm the
/// deployed rules accepted each write.
final class LiveTotalUITests: XCTestCase {
    /// Generous: the other phone's steps take a while to happen.
    private static let peerTimeout: TimeInterval = 90
    private static let screenTimeout: TimeInterval = 10

    private var app: XCUIApplication!
    private var role = ""
    private var projectId = ""

    override func setUpWithError() throws {
        let environment = ProcessInfo.processInfo.environment
        role = environment["LIVE_PARTY_ROLE"] ?? ""
        projectId = environment["LIVE_FIRESTORE_PROJECT"] ?? ""
        guard ["host", "member"].contains(role), !projectId.isEmpty else {
            throw XCTSkip("Set LIVE_PARTY_ROLE and LIVE_FIRESTORE_PROJECT to run the live check")
        }
        continueAfterFailure = false
        app = XCUIApplication()
    }

    func testLiveTotalTipRemoveAndLeave() throws {
        app.launch()
        signIn(as: role == "host" ? "maxi" : "annie")
        if role == "host" { try runHost() } else { try runMember() }
    }

    // MARK: Host (Maxi)

    private func runHost() throws {
        let start = app.buttons["startPartyButton"]
        XCTAssertTrue(start.waitForExistence(timeout: Self.screenTimeout))
        Thread.sleep(forTimeInterval: 1.5)  // the merchant card slides in
        start.tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout))
        let partyId = try XCTUnwrap(element("partyScreen").value as? String)
        print("LIVE party \(partyId)")

        tapEnterTotal()
        replaceAmount(with: "100.00")
        app.buttons["confirmAmountButton"].tap()
        assertSummary(total: "100 dollars", people: "1 person", share: "100 dollars")
        try assertDocument(partyId) { $0.billCents == 10_000 && $0.tip == "custom:0" }

        app.buttons["tipChip-18%"].tap()
        assertSummary(total: "118 dollars", people: "1 person", share: "118 dollars")
        try assertDocument(partyId) { $0.tip == "percent:18" }

        // Annie joins: 5900 each, nothing left over.
        assertSummary(total: "118 dollars", people: "2 people", share: "59 dollars", within: Self.peerTimeout)
        app.buttons["Annie"].tap()
        app.buttons["Remove Annie"].firstMatch.tap()
        assertSummary(total: "118 dollars", people: "1 person", share: "118 dollars")
        try assertDocument(partyId) { $0.memberIds == ["maxi"] }

        // Annie rejoins; the host changes the tip.
        assertSummary(total: "118 dollars", people: "2 people", share: "59 dollars", within: Self.peerTimeout)
        app.buttons["tipChip-20%"].tap()
        assertSummary(total: "120 dollars", people: "2 people", share: "60 dollars")
        try assertDocument(partyId) { $0.tip == "percent:20" && $0.memberIds == ["maxi", "annie"] }

        // Annie leaves.
        assertSummary(total: "120 dollars", people: "1 person", share: "120 dollars", within: Self.peerTimeout)
        try assertDocument(partyId) { $0.memberIds == ["maxi"] && $0.billCents == 10_000 }
    }

    // MARK: Member (Annie)

    private func runMember() throws {
        try joinMaxisParty()
        // The host has 100.00 at 18% by now (or soon): 59.00 each.
        assertSummary(total: "118 dollars", people: "2 people", share: "59 dollars", within: Self.peerTimeout)
        XCTAssertFalse(element("tipChips").exists, "Members get no tip controls")

        // Removed by the host: back on Home with the message and the Join banner.
        XCTAssertTrue(element("homeNotice").waitForExistence(timeout: Self.peerTimeout))
        try joinMaxisParty()
        assertSummary(total: "120 dollars", people: "2 people", share: "60 dollars", within: Self.peerTimeout)

        let leave = app.buttons["leavePartyButton"]
        for _ in 0..<3 where !leave.isHittable { element("partyScreen").swipeUp() }
        leave.tap()
        app.buttons["confirmLeaveParty"].firstMatch.tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(element("joinBanner").waitForExistence(timeout: Self.screenTimeout))
    }

    /// Taps Join on the banner and picks the code read from the party document.
    private func joinMaxisParty() throws {
        let banner = element("joinBanner")
        XCTAssertTrue(banner.waitForExistence(timeout: Self.peerTimeout))
        banner.buttons["Join"].tap()
        XCTAssertTrue(element("joinConfirmationScreen").waitForExistence(timeout: Self.screenTimeout))
        let partyId = try XCTUnwrap(element("joinConfirmationScreen").value as? String,
                                    "The confirmation screen carries the party id")
        let code = try XCTUnwrap(try fetchParty(partyId)?.spokenCode)
        app.buttons.matching(identifier: "codeOption")[code].tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout))
    }

    // MARK: Screen helpers

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func signIn(as user: String) {
        let field = app.textFields["usernameField"]
        XCTAssertTrue(field.waitForExistence(timeout: Self.screenTimeout), "Fresh install: signed out")
        field.tap()
        field.typeText(user)
        app.buttons["Sign in"].tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
    }

    private func tapEnterTotal() {
        let button = app.buttons["enterTotalButton"]
        XCTAssertTrue(button.waitForExistence(timeout: Self.screenTimeout))
        for _ in 0..<3 where !button.isHittable { element("partyScreen").swipeUp() }
        button.tap()
    }

    private func replaceAmount(with text: String) {
        let field = app.textFields["amountField"]
        XCTAssertTrue(field.waitForExistence(timeout: Self.screenTimeout))
        field.tap()
        field.typeText(text)
    }

    private func value(of identifier: String) -> String? {
        let target = element(identifier)
        return target.exists ? target.value as? String : nil
    }

    private func assertSummary(total: String, people: String, share: String,
                               within timeout: TimeInterval = screenTimeout,
                               file: StaticString = #filePath, line: UInt = #line) {
        let expected = NSPredicate { _, _ in
            self.value(of: "partyTotal") == total && self.value(of: "partyHeadcount") == people
                && self.value(of: "myShare") == share
        }
        let result = XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: expected, object: nil)],
                                      timeout: timeout)
        XCTAssertEqual(result, .completed, "Expected \(total) / \(people) / \(share), got "
                       + "\(value(of: "partyTotal") ?? "-") / \(value(of: "partyHeadcount") ?? "-") / "
                       + "\(value(of: "myShare") ?? "-")", file: file, line: line)
    }

    // MARK: Firestore REST (read only)

    private struct PartyDocument {
        let billCents: Int?
        /// "percent:18", "custom:700", or nil.
        let tip: String?
        let memberIds: [String]
        let spokenCode: String?
    }

    private static let spokenNames = [
        "🍕": "pizza", "🍔": "burger", "🌮": "taco", "🍣": "sushi", "🍩": "doughnut", "🍺": "beer",
        "🎉": "party popper", "🔥": "fire", "🚀": "rocket", "🎸": "guitar", "🐶": "dog", "🌵": "cactus",
    ]

    private func assertDocument(_ partyId: String, file: StaticString = #filePath, line: UInt = #line,
                                _ check: (PartyDocument) -> Bool) throws {
        let deadline = Date().addingTimeInterval(5)
        var latest: PartyDocument?
        while Date() < deadline {
            latest = try fetchParty(partyId)
            if let latest, check(latest) { return }
            Thread.sleep(forTimeInterval: 0.5)
        }
        XCTFail("Party document did not match: \(String(describing: latest))", file: file, line: line)
    }

    private func fetchParty(_ partyId: String) throws -> PartyDocument? {
        let url = URL(string: "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents/parties/\(partyId)")!
        var data: Data?
        let done = expectation(description: "fetch")
        URLSession.shared.dataTask(with: url) { body, _, _ in
            data = body
            done.fulfill()
        }.resume()
        wait(for: [done], timeout: 10)
        guard let data,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let fields = json["fields"] as? [String: Any] else { return nil }
        func integer(_ field: Any?) -> Int? {
            ((field as? [String: Any])?["integerValue"] as? String).flatMap { Int($0) }
        }
        let billCents = integer(fields["billCents"])
        var tip: String?
        if let tipFields = ((fields["tip"] as? [String: Any])?["mapValue"] as? [String: Any])?["fields"]
            as? [String: Any], let type = (tipFields["type"] as? [String: Any])?["stringValue"] as? String {
            tip = type == "percent" ? "percent:\(integer(tipFields["percent"]) ?? -1)"
                : "custom:\(integer(tipFields["amountCents"]) ?? -1)"
        }
        let memberValues = ((fields["memberIds"] as? [String: Any])?["arrayValue"] as? [String: Any])?["values"]
            as? [[String: Any]] ?? []
        let codeValues = ((fields["joinCode"] as? [String: Any])?["arrayValue"] as? [String: Any])?["values"]
            as? [[String: Any]] ?? []
        let spokenCode = codeValues.compactMap { $0["stringValue"] as? String }
            .map { Self.spokenNames[$0] ?? $0 }.joined(separator: ", ")
        return PartyDocument(billCents: billCents, tip: tip,
                             memberIds: memberValues.compactMap { $0["stringValue"] as? String },
                             spokenCode: spokenCode.isEmpty ? nil : spokenCode)
    }
}
