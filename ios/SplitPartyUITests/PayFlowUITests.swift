import XCTest

/// Pay (PoC-11, FR-5.1–5.3) and Demo reset (PoC-12) on the built-in fixture,
/// which settles exactly like the Firestore transaction (`PartySettlement`).
/// Seeded balances: Maxi $250.00, Annie $180.50, Christophe $320.75; six seeded
/// payments. See `UITestLaunch` in AppModel.swift for the launch arguments.
final class PayFlowUITests: XCTestCase {
    private static let screenTimeout: TimeInterval = 2
    /// The work order's 1 s bar plus the ticks, the fixture's 0.3 s fake write
    /// and the UI test runner's own polling.
    private static let settleTimeout: TimeInterval = 3
    /// The newest seeded payment (seed_tx_06). The feed is lazy, so rows are
    /// not counted: the payments Pay made are exactly the rows above this one.
    private static let newestSeededMemo = "ice cream run"

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: Pay button

    func testPayWaitsForTheTotal() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi"])
        let pay = payButton
        XCTAssertTrue(pay.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(pay.label, "Pay, unavailable: Add the total")
        XCTAssertFalse(pay.isEnabled)
    }

    func testPayWaitsForFriends() {
        openParty(as: "annie", hostedBy: "annie", members: [], extra: billArguments(cents: 10_000, tip: "18"))
        let pay = payButton
        XCTAssertTrue(pay.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(pay.label, "Pay, unavailable: Waiting for friends to join")
        XCTAssertFalse(pay.isEnabled)
    }

    func testMembersNeverSeeAPayButton() {
        openParty(as: "maxi", hostedBy: "annie", members: ["maxi"], extra: billArguments(cents: 10_000, tip: "18"))
        XCTAssertTrue(element("partyTotal").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(payButton.exists)
    }

    // MARK: Settling

    /// The checkpoint run from Annie's phone: $100.00 at 18% for three.
    func testHostPaysOnceAndEveryoneSettles() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: billArguments(cents: 10_000, tip: "18"))
        let pay = payButton
        XCTAssertTrue(pay.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(pay.label, "Pay, collect 78 dollars and 66 cents from Maxi and Christophe")
        pay.tap()

        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: Self.settleTimeout))
        XCTAssertEqual(element("successAmount").label, "$78.66")
        XCTAssertTrue(app.descendants(matching: .any)["Maxi, paid"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["Christophe, paid"].exists)
        XCTAssertFalse(app.buttons["enterTotalButton"].exists, "Nothing can be edited after Pay")

        app.buttons["backToHomeButton"].tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
        let rows = app.descendants(matching: .any).matching(identifier: "transactionRow")
        assertSeededRowsStart(at: 2)
        for row in [rows.element(boundBy: 0), rows.element(boundBy: 1)] {
            XCTAssertTrue(row.label.contains("paid") && row.label.contains("Annie"), row.label)
            XCTAssertTrue(row.label.contains("Tony's Split Party"), row.label)
            XCTAssertTrue(row.label.contains("$39.33"), row.label)
        }
        XCTAssertTrue(element("splitPartyBanner").waitForExistence(timeout: Self.screenTimeout),
                      "The merchant card is back: the host can start a fresh party")
        assertBalance("$259.16")  // 180.50 + 78.66
    }

    func testMemberSeesTheirPaymentWhenTheHostPays() {
        openParty(as: "christophe", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: billArguments(cents: 10_000, tip: "18") + ["-UITestPartyScript", "3:pay=host"])
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: 3 + Self.settleTimeout))
        XCTAssertEqual(element("successAmount").label, "$39.33")
        XCTAssertTrue(app.staticTexts["You paid"].exists)
        XCTAssertFalse(payButton.exists)
        app.buttons["backToHomeButton"].tap()
        XCTAssertFalse(element("joinBanner").exists, "No Join banner for a paid party")
        assertBalance("$281.42")  // 320.75 − 39.33
    }

    func testNoTipChosenSettlesTheTwoFriendSharesOnly() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000"])
        payButton.tap()
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: Self.settleTimeout))
        XCTAssertEqual(element("successAmount").label, "$66.66", "2 × 33.33; the host keeps 33.34")
    }

    func testDoubleTapPaysOnce() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: billArguments(cents: 10_000, tip: "18"))
        let pay = payButton
        XCTAssertTrue(pay.waitForExistence(timeout: Self.screenTimeout))
        pay.tap()
        if pay.exists && pay.isHittable { pay.tap() }
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: Self.settleTimeout))
        app.buttons["backToHomeButton"].tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
        assertSeededRowsStart(at: 2)  // one payment per friend
        assertBalance("$259.16")
    }

    func testRemovedMemberIsNotSettled() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: billArguments(cents: 10_000, tip: "18") + ["-UITestPartyScript", "1:remove=christophe"])
        let pay = payButton
        let halfReady = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "Pay, collect 59 dollars from Maxi"), object: pay)
        XCTAssertEqual(XCTWaiter().wait(for: [halfReady], timeout: 4), .completed)
        pay.tap()
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: Self.settleTimeout))
        XCTAssertEqual(element("successAmount").label, "$59.00")
        XCTAssertFalse(app.descendants(matching: .any)["Christophe, paid"].exists)
    }

    func testExpiredPartyCannotBePaid() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi"],
                  extra: billArguments(cents: 10_000, tip: "18") + ["-UITestFriendPartyAge", "288"])
        XCTAssertTrue(app.staticTexts["This party has expired"].waitForExistence(timeout: 15))
        XCTAssertEqual(payButton.label, "Pay, unavailable: Party expired")
        XCTAssertFalse(payButton.isEnabled)
        app.buttons["backToHomeButton"].tap()
        assertSeededRowsStart(at: 0)
        assertBalance("$180.50")
    }

    func testHostStartsAFreshPartyAfterPaying() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi"], extra: billArguments(cents: 10_000, tip: "18"))
        let paidPartyId = element("partyScreen").value as? String
        payButton.tap()
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: Self.settleTimeout))
        app.buttons["backToHomeButton"].tap()
        let start = app.buttons["startPartyButton"]
        XCTAssertTrue(start.waitForExistence(timeout: 3))
        start.tap()
        XCTAssertTrue(element("payButton").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertNotEqual(element("partyScreen").value as? String, paidPartyId)
        XCTAssertEqual(payButton.label, "Pay, unavailable: Add the total")
    }

    // MARK: Demo reset

    func testDemoResetPutsTheSeededWorldBack() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: billArguments(cents: 10_000, tip: "18"))
        payButton.tap()
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: Self.settleTimeout))
        app.buttons["backToHomeButton"].tap()
        assertBalance("$259.16")

        tapDemoReset()
        XCTAssertTrue(element("demoResetDone").waitForExistence(timeout: Self.settleTimeout))
        XCTAssertEqual(element("balance").label, "$180.50")
        app.buttons["Home"].tap()
        assertSeededRowsStart(at: 0)
        XCTAssertFalse(element("usernameField").exists, "Still signed in")
    }

    func testDemoResetFailureChangesNothing() {
        openParty(as: "annie", hostedBy: "annie", members: ["maxi"],
                  extra: billArguments(cents: 10_000, tip: "18") + ["-UITestDemoReset", "fail"])
        app.navigationBars.buttons.firstMatch.tap()
        tapDemoReset()
        XCTAssertTrue(element("demoResetError").waitForExistence(timeout: Self.settleTimeout))
        XCTAssertTrue(element("demoResetButton").isEnabled, "Can be tapped again")
        XCTAssertEqual(element("balance").label, "$180.50")
        app.buttons["Home"].tap()
        XCTAssertTrue(element("backToPartyCard").waitForExistence(timeout: Self.screenTimeout),
                      "The open party is untouched")
    }

    func testResetFromAnotherPhoneSendsThePartyScreenHome() {
        openParty(as: "maxi", hostedBy: "annie", members: ["maxi"],
                  extra: ["-UITestPartyScript", "2:reset=demo"])
        // Home stays alive under a pushed screen, so wait for the party screen to go.
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"),
                                             object: element("partyScreen"))
        XCTAssertEqual(XCTWaiter().wait(for: [gone], timeout: 2 + Self.settleTimeout), .completed)
        XCTAssertTrue(element("homeScreen").exists)
        XCTAssertFalse(element("joinBanner").exists)
        XCTAssertFalse(element("backToPartyCard").exists)
    }

    // MARK: Helpers

    private var payButton: XCUIElement { app.buttons["payButton"] }

    private func billArguments(cents: Int, tip: String) -> [String] {
        ["-UITestFriendPartyBill", String(cents), "-UITestFriendPartyTip", tip]
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    /// The first `index` rows are new; row `index` is the newest seeded one.
    private func assertSeededRowsStart(at index: Int, file: StaticString = #filePath, line: UInt = #line) {
        let rows = app.descendants(matching: .any).matching(identifier: "transactionRow")
        let seeded = rows.element(boundBy: index)
        let found = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label CONTAINS %@", Self.newestSeededMemo), object: seeded)
        XCTAssertEqual(XCTWaiter().wait(for: [found], timeout: Self.settleTimeout), .completed,
                       "Row \(index) should be the newest seeded payment", file: file, line: line)
    }

    private func tapDemoReset() {
        app.buttons["Me"].tap()
        let reset = element("demoResetButton")
        XCTAssertTrue(reset.waitForExistence(timeout: Self.screenTimeout))
        for _ in 0..<4 where !reset.isHittable { element("meScreen").swipeUp() }
        reset.tap()
        // The system dialog exposes its buttons twice; take the first.
        let confirm = app.buttons["confirmDemoReset"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: Self.screenTimeout))
        confirm.tap()
    }

    /// Opens Me and waits for the balance to read `expected`.
    private func assertBalance(_ expected: String, file: StaticString = #filePath, line: UInt = #line) {
        app.buttons["Me"].tap()
        let balance = element("balance")
        let matches = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", expected),
                                                object: balance)
        XCTAssertEqual(XCTWaiter().wait(for: [matches], timeout: Self.settleTimeout), .completed,
                       "Balance \(balance.exists ? balance.label : "-"), expected \(expected)",
                       file: file, line: line)
    }

    /// Signs in as `user` while `hostId` hosts an open party with `members` in
    /// it; signing in while in an open party lands straight on its screen.
    private func openParty(as user: String, hostedBy hostId: String, members: [String], extra: [String] = []) {
        var arguments = ["-UITestFixture", "seeded", "-UITestResetSession", "YES", "-UITestFriendParty", hostId]
        if !members.isEmpty { arguments += ["-UITestFriendPartyMembers", members.joined(separator: ",")] }
        app.launchArguments = arguments + extra
        app.launch()
        let usernameField = app.textFields["usernameField"]
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout))
        usernameField.tap()
        usernameField.typeText(user)
        app.buttons["Sign in"].tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout))
    }
}
