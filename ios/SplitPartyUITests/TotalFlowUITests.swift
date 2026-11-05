import XCTest

/// The total, tip and live split (PoC-10, FR-3, FR-4) and leaving/removal
/// (FR-2.5) on the built-in fixture. Fixture parties are hosted at Tony's with
/// the code 🍕🍺; see `UITestLaunch` in AppModel.swift for the launch arguments.
final class TotalFlowUITests: XCTestCase {
    private static let screenTimeout: TimeInterval = 2
    /// The work order's 1 s bar plus a little for the UI test runner itself.
    private static let liveTimeout: TimeInterval = 1.5

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: Keypad entry

    func testHostTypesTheTotalAndOutOfRangeAmountsAreBlocked() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie"])
        XCTAssertTrue(element("addTotalCard").waitForExistence(timeout: Self.screenTimeout))
        app.buttons["enterTotalButton"].tap()
        XCTAssertTrue(amountField.waitForExistence(timeout: Self.screenTimeout))

        for blocked in ["0", "10000.00", "12.345"] {
            replaceAmount(with: blocked)
            app.buttons["confirmAmountButton"].tap()
            XCTAssertTrue(element("amountError").waitForExistence(timeout: Self.screenTimeout),
                          "\(blocked) is blocked inline")
            XCTAssertTrue(amountField.exists, "Nothing written: still on the keypad")
        }

        replaceAmount(with: "48.50")
        app.buttons["confirmAmountButton"].tap()
        XCTAssertTrue(element("partyTotal").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(amountField.exists)
        XCTAssertEqual(element("partyTotal").value as? String, "48 dollars and 50 cents")
    }

    func testRangeEndsAreAccepted() {
        openParty(as: "maxi", hostedBy: "maxi", members: [])
        for accepted in ["0.01", "9999.99"] {
            let button = app.buttons["enterTotalButton"]
            XCTAssertTrue(button.waitForExistence(timeout: Self.screenTimeout))
            XCTAssertFalse(amountField.exists)
            // Once a bill exists the button sits under the tip chips.
            for _ in 0..<3 where !button.isHittable { element("partyScreen").swipeUp() }
            button.tap()
            replaceAmount(with: accepted)
            app.buttons["confirmAmountButton"].tap()
            XCTAssertFalse(element("amountError").waitForExistence(timeout: 1), "\(accepted) is accepted")
            XCTAssertTrue(element("partyTotal").waitForExistence(timeout: Self.screenTimeout))
            let closed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: amountField)
            XCTAssertEqual(XCTWaiter().wait(for: [closed], timeout: Self.screenTimeout), .completed)
        }
    }

    func testMemberSeesWaitingForTheTotalWithNoEntryControls() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie"])
        XCTAssertTrue(app.staticTexts["Waiting for the total"].waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(app.buttons["scanReceiptButton"].exists)
        XCTAssertFalse(app.buttons["enterTotalButton"].exists)
    }

    // MARK: Receipt scan

    /// A Tony's-style receipt drawn in code by the Debug scan path (no photo is
    /// bundled) and read by the real Vision pipeline. Label and amount are split
    /// by a tab, lines by "|".
    private static let tonysReceipt = [
        "TONY'S", "Greenwich Ave", "2 Margherita\t$36.00", "1 Caesar\t$12.50",
        "Subtotal\t$48.50", "Tax 6.35%\t$3.08", "TOTAL\t$51.58", "Tip 18%\t$8.73",
    ].joined(separator: "|")

    func testScanFindsTheTotalLineAndTheHostConfirmsItWithinThreeSeconds() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie"], extra: ["-UITestReceiptEnv", Self.tonysReceipt])
        let scan = app.buttons["scanReceiptButton"]
        XCTAssertTrue(scan.waitForExistence(timeout: Self.screenTimeout))
        scan.tap()
        // The Debug path "captures" as soon as the scan screen is up, so time from there.
        XCTAssertTrue(element("receiptScanScreen").waitForExistence(timeout: Self.screenTimeout))
        let captured = Date()
        XCTAssertTrue(element("confirmTotalScreen").waitForExistence(timeout: 3), "Result within 3 s")
        XCTAssertLessThan(Date().timeIntervalSince(captured), 3)
        XCTAssertEqual(amountField.value as? String, "51.58", "The TOTAL line, not subtotal, tax or tip")
        XCTAssertFalse(element("partyTotal").exists, "Nothing written before the host confirms")
        app.buttons["confirmAmountButton"].tap()
        XCTAssertTrue(element("partyTotal").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(element("partyTotal").value as? String, "51 dollars and 58 cents")
    }

    func testBlankPageShowsTheNoTotalMessageOnTheKeypadAndWritesNothing() {
        openParty(as: "maxi", hostedBy: "maxi", members: [], extra: ["-UITestReceiptEnv", "blank"])
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(element("entryNotice").waitForExistence(timeout: 3))
        XCTAssertTrue(amountField.exists, "Lands on the keypad")
        app.buttons["cancelAmountButton"].tap()
        XCTAssertTrue(element("addTotalCard").waitForExistence(timeout: Self.screenTimeout), "Still no total")
    }

    func testUnreadableTotalLineShowsTheNoTotalMessage() {
        openParty(as: "maxi", hostedBy: "maxi", members: [],
                  extra: ["-UITestReceiptEnv", "TONY'S|Subtotal\t$48.50|Tax\t$3.08"])
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(element("entryNotice").waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["We couldn't find a total on that receipt. Type it instead."].exists)
    }

    func testCameraDeniedExplainsAndOffersEnterTotal() {
        openParty(as: "maxi", hostedBy: "maxi", members: [], extra: ["-UITestCameraDenied", "YES"])
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(app.staticTexts["Camera access is off"].waitForExistence(timeout: Self.screenTimeout))
        app.buttons["cameraUnavailableEnterTotal"].tap()
        replaceAmount(with: "48.50")
        app.buttons["confirmAmountButton"].tap()
        XCTAssertTrue(element("partyTotal").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(element("partyTotal").value as? String, "48 dollars and 50 cents", "billCents 4850")
    }

    func testNoCameraOnTheSimulatorExplainsAndOffersEnterTotal() {
        openParty(as: "maxi", hostedBy: "maxi", members: [])
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(app.staticTexts["No camera available"].waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(app.buttons["cameraUnavailableEnterTotal"].exists)
    }

    // MARK: Tip and live split

    func testEighteenPercentOnOneHundredSplitsThreeWays() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000"])
        XCTAssertTrue(element("tipChips").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(app.buttons["tipChip-0%"].isSelected, "0% until the host picks one")
        app.buttons["tipChip-18%"].tap()
        assertSummary(total: "118 dollars", headcount: "3 people", share: "39 dollars and 34 cents")
        XCTAssertEqual(element("partyTip").value as? String, "18 percent, 18 dollars")
        XCTAssertTrue(app.buttons["tipChip-18%"].isSelected)
    }

    func testCustomTipOfSevenDollars() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "custom:700"])
        // A member: 35.66 (the host carries the leftover: 35.68).
        assertSummary(total: "107 dollars", headcount: "3 people", share: "35 dollars and 66 cents")
    }

    func testHostEntersACustomTip() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000"])
        XCTAssertTrue(app.buttons["tipChip-Custom"].waitForExistence(timeout: Self.screenTimeout))
        app.buttons["tipChip-Custom"].tap()
        replaceAmount(with: "7.00")
        app.buttons["confirmAmountButton"].tap()
        assertSummary(total: "107 dollars", headcount: "3 people", share: "35 dollars and 68 cents")
    }

    func testMemberSeesTheTipReadOnlyAndSharesFollowEveryChange() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "18",
                          "-UITestPartyScript", "6:join=christophe;9:bill=20000"])
        assertSummary(total: "118 dollars", headcount: "2 people", share: "59 dollars")
        XCTAssertFalse(element("tipChips").exists, "No tip controls for members")
        XCTAssertFalse(app.buttons["scanReceiptButton"].exists)
        XCTAssertFalse(app.buttons["enterTotalButton"].exists)
        // Christophe joins: share and headcount change together.
        assertSummary(total: "118 dollars", headcount: "3 people", share: "39 dollars and 33 cents", within: 8)
        // The host re-enters 200.00.
        assertSummary(total: "236 dollars", headcount: "3 people", share: "78 dollars and 66 cents", within: 5)
    }

    func testMemberSeesWaitingUntilTheHostConfirmsABill() {
        openParty(as: "christophe", hostedBy: "maxi", members: ["christophe"],
                  extra: ["-UITestPartyScript", "6:bill=3333;7:tip=15"])
        XCTAssertTrue(app.staticTexts["Waiting for the total"].waitForExistence(timeout: Self.screenTimeout))
        // 33.33 at 15%: 5.00 of tip (half up), total 38.33, two people.
        assertSummary(total: "38 dollars and 33 cents", headcount: "2 people", share: "19 dollars and 16 cents",
                      within: 10)
        XCTAssertEqual(element("partyTip").value as? String, "15 percent, 5 dollars")
    }

    // MARK: Removing and leaving (FR-2.5)

    func testHostRemovesAMemberAndSharesRecompute() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "18"])
        assertSummary(total: "118 dollars", headcount: "3 people", share: "39 dollars and 34 cents")
        XCTAssertFalse(app.buttons["leavePartyButton"].exists, "The host has no leave action")
        XCTAssertFalse(app.buttons["Maxi, host"].exists, "The host's own seat is not removable")
        app.buttons["Christophe"].tap()
        // iOS 26 exposes a confirmation dialog's buttons twice.
        let confirm = app.buttons["Remove Christophe"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: Self.screenTimeout))
        confirm.tap()
        assertSummary(total: "118 dollars", headcount: "2 people", share: "59 dollars")
        XCTAssertFalse(app.buttons["Christophe"].exists, "Avatar leaves the member row")
    }

    func testRemovedMemberLandsHomeWithTheMessageAndCanRejoin() {
        openParty(as: "christophe", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "18",
                          "-UITestPartyScript", "5:remove=christophe"])
        assertSummary(total: "118 dollars", headcount: "3 people", share: "39 dollars and 33 cents")
        XCTAssertTrue(element("homeNotice").waitForExistence(timeout: 8))
        XCTAssertTrue(app.staticTexts["You were removed from Maxi's party"].exists
                      || element("homeNotice").label.contains("You were removed from Maxi's party"))
        let joinBanner = element("joinBanner")
        XCTAssertTrue(joinBanner.waitForExistence(timeout: Self.screenTimeout), "Join banner is back")

        joinBanner.buttons["Join"].tap()
        let correct = app.buttons.matching(identifier: "codeOption")["pizza, beer"]
        XCTAssertTrue(correct.waitForExistence(timeout: Self.screenTimeout))
        correct.tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout))
        assertSummary(total: "118 dollars", headcount: "3 people", share: "39 dollars and 33 cents")
    }

    func testMemberLeavesAndHomeOffersTheJoinBanner() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "18"])
        let leave = app.buttons["leavePartyButton"]
        XCTAssertTrue(leave.waitForExistence(timeout: Self.screenTimeout))
        for _ in 0..<3 where !leave.isHittable { element("partyScreen").swipeUp() }
        leave.tap()
        let confirm = app.buttons["confirmLeaveParty"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: Self.screenTimeout))
        confirm.tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(element("joinBanner").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(element("homeNotice").exists, "Leaving on purpose is not a removal")
    }

    // MARK: Expiry

    func testExpiryFreezesTheShareAndRemovesTheControls() {
        // Created 290 s ago: expires about 10 s after launch.
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "20",
                          "-UITestFriendPartyAge", "290"])
        assertSummary(total: "120 dollars", headcount: "2 people", share: "60 dollars")
        XCTAssertTrue(app.staticTexts["This party has expired"].waitForExistence(timeout: 12))
        assertSummary(total: "120 dollars", headcount: "2 people", share: "60 dollars")
        XCTAssertFalse(element("tipChips").exists)
        XCTAssertFalse(app.buttons["scanReceiptButton"].exists)
        XCTAssertFalse(app.buttons["enterTotalButton"].exists)
    }

    // MARK: Helpers

    /// Waits until total, headcount and own share all read as expected. The
    /// default wait is generous: the predicate is polled about once a second and
    /// each poll reads three elements, so the runner cannot time the work order's
    /// 1 s bar (that is a stopwatch check on phones; see the slice summary).
    private func assertSummary(total: String, headcount: String, share: String,
                               within timeout: TimeInterval = 4,
                               file: StaticString = #filePath, line: UInt = #line) {
        let expected = NSPredicate { _, _ in
            self.value(of: "partyTotal") == total && self.value(of: "partyHeadcount") == headcount
                && self.value(of: "myShare") == share
        }
        let matched = XCTNSPredicateExpectation(predicate: expected, object: nil)
        let result = XCTWaiter().wait(for: [matched], timeout: timeout)
        XCTAssertEqual(result, .completed,
                       "Expected \(total) / \(headcount) / \(share), got "
                       + "\(value(of: "partyTotal") ?? "-") / \(value(of: "partyHeadcount") ?? "-") / "
                       + "\(value(of: "myShare") ?? "-")", file: file, line: line)
    }

    /// Nil while the element is not on screen (reading `.value` of a missing
    /// element fails the test instead of letting the wait continue).
    private func value(of identifier: String) -> String? {
        let target = element(identifier)
        return target.exists ? target.value as? String : nil
    }

    private var amountField: XCUIElement { app.textFields["amountField"] }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func replaceAmount(with text: String) {
        amountField.tap()
        let current = (amountField.value as? String) ?? ""
        amountField.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count + 2))
        amountField.typeText(text)
    }

    /// Signs in as `user` while `hostId` hosts an open party with `members` in it;
    /// signing in while in an open party lands straight on its screen.
    private func openParty(as user: String, hostedBy hostId: String, members: [String], extra: [String] = []) {
        var arguments = ["-UITestFixture", "seeded", "-UITestResetSession", "YES", "-UITestFriendParty", hostId]
        if !members.isEmpty { arguments += ["-UITestFriendPartyMembers", members.joined(separator: ",")] }
        app.launchArguments = applyReceiptEnvironment(arguments + extra, to: app)
        app.launch()
        let usernameField = app.textFields["usernameField"]
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout))
        usernameField.tap()
        usernameField.typeText(user)
        app.buttons["Sign in"].tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout))
    }
}

/// Moves a `"-UITestReceiptEnv", text` pair out of the launch arguments into the
/// `UITestReceipt` environment variable (argument values are parsed as property
/// lists, which mangles a receipt's spaces and apostrophes).
func applyReceiptEnvironment(_ arguments: [String], to app: XCUIApplication) -> [String] {
    guard let index = arguments.firstIndex(of: "-UITestReceiptEnv"), index + 1 < arguments.count else {
        app.launchEnvironment["UITestReceipt"] = nil
        return arguments
    }
    app.launchEnvironment["UITestReceipt"] = arguments[index + 1]
    var remaining = arguments
    remaining.removeSubrange(index...index + 1)
    return remaining
}
