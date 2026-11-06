import XCTest

/// Host cancel (PRD US-12/13, FR-2.8–2.10) on the built-in fixture, which follows
/// the same rules as the Firestore transaction. Seeded balances: Maxi $250.00,
/// Annie $180.50, Christophe $320.75. See `UITestLaunch` in AppModel.swift.
final class CancelFlowUITests: XCTestCase {
    private static let screenTimeout: TimeInterval = 2

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    func testDismissingTheSheetKeepsThePartyAndConfirmingEndsIt() {
        openParty(as: "annie", hostedBy: "annie", members: [])
        let cancel = app.buttons["cancelPartyButton"]
        XCTAssertTrue(cancel.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(cancel.label, "Cancel party")
        // It sits under Pay, below the fold behind the floating tab bar until scrolled.
        element("partyScreen").swipeUp()

        cancel.tap()
        XCTAssertTrue(app.buttons["confirmCancelParty"].firstMatch.waitForExistence(timeout: Self.screenTimeout))
        // Dismiss without confirming: "Keep party" where the system shows it, else a tap
        // outside the popover (iOS 26+ drops the cancel-role button from it).
        let keep = app.buttons["Keep party"].firstMatch
        if keep.exists && keep.isHittable {
            keep.tap()
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)).tap()
        }
        XCTAssertTrue(app.buttons["confirmCancelParty"].firstMatch.waitForNonExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(app.buttons["payButton"].waitForExistence(timeout: Self.screenTimeout),
                      "Keep party leaves the party open")

        element("partyScreen").swipeUp()
        cancel.tap()
        let confirm = app.buttons["confirmCancelParty"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: Self.screenTimeout))
        confirm.tap()

        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(element("backToPartyCard").exists)
        let start = app.buttons["startPartyButton"]
        XCTAssertTrue(start.waitForExistence(timeout: 3), "The merchant card is back straight away")
        start.tap()
        XCTAssertTrue(app.buttons["cancelPartyButton"].waitForExistence(timeout: Self.screenTimeout),
                      "A new party starts with no cooldown")
    }

    func testJoineesNeverSeeTheCancelControl() {
        openParty(as: "maxi", hostedBy: "annie", members: ["maxi"])
        XCTAssertTrue(app.buttons["leavePartyButton"].waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(app.buttons["cancelPartyButton"].exists)
    }

    func testAJoineeSeesCancelledByHostAndNothingIsCharged() {
        openParty(as: "christophe", hostedBy: "annie", members: ["maxi", "christophe"],
                  extra: ["-UITestFriendPartyBill", "10000", "-UITestFriendPartyTip", "18",
                          "-UITestPartyScript", "2:cancel=host"])
        XCTAssertTrue(app.staticTexts["Party cancelled by host"].waitForExistence(timeout: 2 + 3))
        XCTAssertTrue(app.staticTexts["Nobody was charged."].exists)
        XCTAssertFalse(app.staticTexts["This party has expired"].exists, "Never labelled as expired")
        XCTAssertFalse(element("partyTotal").exists, "The live split is gone")
        XCTAssertFalse(app.buttons["leavePartyButton"].exists)
        app.buttons["backToHomeButton"].tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(element("joinBanner").exists)
        XCTAssertFalse(element("backToPartyCard").exists)
        app.buttons["Me"].tap()
        let balance = element("balance")
        XCTAssertTrue(balance.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(balance.label, "$320.75")
    }

    // MARK: Helpers

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
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
