import XCTest

/// Custom (uneven) shares on the built-in fixture: every phone shows each
/// member's share from the extended split rule with a custom / remainder marker,
/// and only the host with the Me tab's "Custom shares" switch on can change them.
/// Fixture parties are hosted at Tony's; see `UITestLaunch` in AppModel.swift.
final class CustomSharesUITests: XCTestCase {
    private static let screenTimeout: TimeInterval = 2
    private static let liveTimeout: TimeInterval = 4

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: Display (every phone, any switch state)

    func testJoineeSeesTheHostsCustomSharesWithMarkersAndNoControls() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000", "-UITestFriendPartyCustomShares", "maxi:1000,annie:3500"])
        assertShare("maxi", is: "10 dollars, custom amount")
        assertShare("annie", is: "35 dollars, custom amount")
        assertShare("christophe", is: "15 dollars, remainder, splits the rest equally")
        XCTAssertEqual(element("myShare").value as? String, "35 dollars")
        XCTAssertFalse(shareRow("christophe").isHittableButton(in: app), "Joinees never get amount controls")
    }

    func testPartyWithoutCustomAmountsShowsThePlainEqualSplit() {
        openParty(as: "christophe", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "18"])
        assertShare("maxi", is: "39 dollars and 34 cents, equal split")
        assertShare("annie", is: "39 dollars and 33 cents, equal split")
        assertShare("christophe", is: "39 dollars and 33 cents, equal split")
        XCTAssertFalse(element("customSharesProblem").exists)
    }

    func testSharesFollowTheHostsChangesAndALeavingMembersAmountGoesWithThem() {
        openParty(as: "christophe", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000",
                          "-UITestPartyScript", "5:custom=maxi:1000;7:custom=annie:3500;9:remove=annie"])
        assertShare("christophe", is: "20 dollars, equal split")
        assertShare("christophe", is: "25 dollars, remainder, splits the rest equally", within: 8)
        assertShare("christophe", is: "15 dollars, remainder, splits the rest equally", within: 6)
        // Annie leaves holding $35: her amount goes with her, the shares still sum to $60.
        assertShare("christophe", is: "50 dollars, remainder, splits the rest equally", within: 6)
        assertShare("maxi", is: "10 dollars, custom amount")
        XCTAssertFalse(shareRow("annie").exists)
    }

    // MARK: Host with the switch on

    func testHostSetsTenAndThirtyFiveRefusesSeventyAndClearsBackToEqual() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000"])
        XCTAssertFalse(shareRow("annie").isHittableButton(in: app), "No controls while the switch is off")
        turnCustomSharesOnAndReturnToTheParty()

        setShare("maxi", to: "10")
        setShare("annie", to: "35")
        assertShare("maxi", is: "10 dollars, custom amount")
        assertShare("annie", is: "35 dollars, custom amount")
        assertShare("christophe", is: "15 dollars, remainder, splits the rest equally")
        XCTAssertEqual(payButton.label, "Collect 50 dollars from Annie and Christophe")

        // $35 more for Christophe would be $70 of a $60 total: refused, nothing changes.
        setShare("christophe", to: "35")
        let error = element("customShareError")
        XCTAssertTrue(error.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(error.label.contains("$70.00") && error.label.contains("$60.00"), error.label)
        app.buttons["cancelCustomShareButton"].tap()
        assertShare("christophe", is: "15 dollars, remainder, splits the rest equally")
        XCTAssertTrue(payButton.isEnabled)

        shareRow("annie").tap()
        app.buttons["clearCustomShareButton"].tap()
        assertShare("maxi", is: "10 dollars, custom amount")
        assertShare("annie", is: "25 dollars, remainder, splits the rest equally")
        assertShare("christophe", is: "25 dollars, remainder, splits the rest equally")
    }

    func testKeypadRefusesZeroAndThreeDecimals() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie"], extra: ["-UITestFriendPartyBill", "6000"])
        turnCustomSharesOnAndReturnToTheParty()
        setShare("annie", to: "0")
        XCTAssertTrue(element("customShareError").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(element("customShareError").label, "The amount must be at least $0.01.")
        app.buttons["cancelCustomShareButton"].tap()
        setShare("annie", to: "1.234")
        XCTAssertEqual(element("customShareError").label, "Use at most two decimals, like 48.50.")
        app.buttons["cancelCustomShareButton"].tap()
        assertShare("annie", is: "30 dollars, equal split")
    }

    func testLoweringTheBillUnderTheAmountsDisablesCollectUntilTheHostFixesThem() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000", "-UITestFriendPartyCustomShares", "maxi:1000,annie:3500",
                          "-UITestPartyScript", "4:bill=4000"])
        let problem = element("customSharesProblem")
        XCTAssertTrue(problem.waitForExistence(timeout: 8))
        XCTAssertTrue(problem.label.contains("$45.00") && problem.label.contains("$40.00"), problem.label)
        XCTAssertEqual(payButton.label, "Collect, unavailable: Fix the custom amounts")
        XCTAssertFalse(payButton.isEnabled)

        turnCustomSharesOnAndReturnToTheParty()
        shareRow("annie").tap()
        app.buttons["clearCustomShareButton"].tap()
        assertShare("annie", is: "15 dollars, remainder, splits the rest equally")
        XCTAssertFalse(element("customSharesProblem").exists)
        XCTAssertTrue(payButton.isEnabled)
    }

    func testSwitchOffShowsCustomSharesWithoutControlsAndTheSwitchSurvivesRelaunch() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000", "-UITestFriendPartyCustomShares", "maxi:1000,annie:3500"])
        assertShare("christophe", is: "15 dollars, remainder, splits the rest equally")
        XCTAssertFalse(shareRow("annie").isHittableButton(in: app), "Switch off: no controls")
        shareRow("annie").tap()
        XCTAssertFalse(element("customShareEditor").exists, "Tapping a share does nothing with the switch off")

        tapTab("Me")
        let toggle = element("customSharesToggle")
        XCTAssertTrue(toggle.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(toggle.value as? String, "0", "Off on a fresh install")
        XCTAssertEqual(toggle.label, "Custom shares")
        toggle.switches.firstMatch.tap()
        XCTAssertEqual(toggle.value as? String, "1")

        app.terminate()
        app.launchArguments = app.launchArguments.map { $0 == "YES" ? "NO" : $0 }
        app.launch()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout), "Relaunch reopens the party")
        XCTAssertTrue(shareRow("annie").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(shareRow("annie").isHittableButton(in: app), "Switch still on after relaunch")
        XCTAssertEqual(shareRow("annie").label, "Annie's share")
    }

    // MARK: Collect

    func testCollectChargesTheCustomAndRemainderShares() {
        openParty(as: "maxi", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000", "-UITestFriendPartyCustomShares", "maxi:1000,annie:3500"])
        XCTAssertTrue(payButton.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(payButton.label, "Collect 50 dollars from Annie and Christophe")
        payButton.tap()
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: 8))
        XCTAssertEqual(element("successAmount").label, "$50.00")
        app.buttons["backToHomeButton"].tap()
        let rows = app.descendants(matching: .any).matching(identifier: "transactionRow")
        XCTAssertTrue(rows.firstMatch.waitForExistence(timeout: Self.screenTimeout))
        let amounts = [rows.element(boundBy: 0).label, rows.element(boundBy: 1).label]
        XCTAssertTrue(amounts.contains { $0.contains("Annie") && $0.contains("$35.00") }, "\(amounts)")
        XCTAssertTrue(amounts.contains { $0.contains("Christophe") && $0.contains("$15.00") }, "\(amounts)")
    }

    func testJoineeWithAZeroCustomShareIsNotCharged() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000", "-UITestFriendPartyCustomShares", "annie:0",
                          "-UITestPartyScript", "4:pay=host"])
        assertShare("annie", is: "0 dollars, custom amount")
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: 10))
        XCTAssertEqual(element("successAmount").label, "$0.00")
        app.buttons["backToHomeButton"].tap()
        let rows = app.descendants(matching: .any).matching(identifier: "transactionRow")
        XCTAssertTrue(rows.firstMatch.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(rows.element(boundBy: 0).label.contains("Christophe")
                      && rows.element(boundBy: 0).label.contains("$30.00"), rows.element(boundBy: 0).label)
        XCTAssertFalse(rows.element(boundBy: 1).label.contains("Tony's Split Party"),
                       "Only Christophe's payment was created")
    }

    func testJoineesYouPaidShowsTheirCustomShare() {
        openParty(as: "annie", hostedBy: "maxi", members: ["annie", "christophe"],
                  extra: ["-UITestFriendPartyBill", "6000", "-UITestFriendPartyCustomShares", "maxi:1000,annie:3500",
                          "-UITestPartyScript", "4:pay=host"])
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: 10))
        XCTAssertEqual(element("successAmount").label, "$35.00")
    }

    // MARK: Helpers

    private var payButton: XCUIElement { app.buttons["payButton"] }

    private func tapTab(_ title: String) {
        element("bottomBar").buttons[title].tap()
    }

    /// Me tab → "Custom shares" on → Home → "Back to your party".
    private func turnCustomSharesOnAndReturnToTheParty() {
        tapTab("Me")
        let toggle = element("customSharesToggle")
        XCTAssertTrue(toggle.waitForExistence(timeout: Self.screenTimeout))
        if toggle.value as? String != "1" { toggle.switches.firstMatch.tap() }
        tapTab("Home")
        let backToParty = app.buttons["backToPartyCard"]
        XCTAssertTrue(backToParty.waitForExistence(timeout: Self.screenTimeout))
        backToParty.tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.screenTimeout))
    }

    /// Opens the inline keypad on a member's share, types `amount` and taps Set.
    private func setShare(_ memberId: String, to amount: String) {
        let row = shareRow(memberId)
        XCTAssertTrue(row.waitForExistence(timeout: Self.screenTimeout))
        row.tap()
        let field = app.textFields["customShareField"]
        XCTAssertTrue(field.waitForExistence(timeout: Self.screenTimeout))
        field.tap()
        let current = (field.value as? String) ?? ""
        if current != "0.00" {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count + 2))
        }
        field.typeText(amount)
        app.buttons["setCustomShareButton"].tap()
    }

    private func shareRow(_ memberId: String) -> XCUIElement {
        element("memberShare-\(memberId)")
    }

    /// Waits until the member's row reads `value` (amount and marker).
    private func assertShare(_ memberId: String, is value: String, within timeout: TimeInterval = liveTimeout,
                             file: StaticString = #filePath, line: UInt = #line) {
        let row = shareRow(memberId)
        let expected = NSPredicate { _, _ in row.exists && row.value as? String == value }
        let result = XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: expected, object: nil)],
                                      timeout: timeout)
        XCTAssertEqual(result, .completed,
                       "\(memberId): expected \(value), got \(row.exists ? (row.value as? String ?? "-") : "no row")",
                       file: file, line: line)
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    /// Signs in as `user` while `hostId` hosts an open party with `members` in it;
    /// signing in while in an open party lands straight on its screen.
    private func openParty(as user: String, hostedBy hostId: String, members: [String], extra: [String] = [],
                           resetsSession: Bool = true) {
        var arguments = ["-UITestFixture", "seeded", "-UITestResetSession", resetsSession ? "YES" : "NO",
                         "-UITestFriendParty", hostId]
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

private extension XCUIElement {
    /// True when this element is exposed as a button (i.e. tappable for the user).
    func isHittableButton(in app: XCUIApplication) -> Bool {
        exists && elementType == .button
    }
}
