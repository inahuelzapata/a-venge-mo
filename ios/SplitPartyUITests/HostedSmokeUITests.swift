import XCTest

/// TEMPORARY (not committed): drives the real app against the hosted project.
final class HostedSmokeUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        guard ProcessInfo.processInfo.environment["HOSTED_SMOKE"] == "1" else { throw XCTSkip("hosted only") }
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = []
    }

    func test1CreateReenterWrongCodeAndJoin() {
        app.launch()
        signInFresh("maxi")
        tapStartWhenSettled()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: 6), "party created on hosted")
        let partyId = element("partyScreen").value as? String
        let code = element("joinCode").label.replacingOccurrences(of: "Join code: ", with: "")
        print("HOSTED party \(partyId ?? "?") code \(code) countdown \(element("partyCountdown").label)")
        XCTAssertEqual(seats.allElementsBoundByIndex.map(\.label), ["Maxi, host"])
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["backToPartyCard"].waitForExistence(timeout: 4))
        app.buttons["backToPartyCard"].tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: 4))
        XCTAssertEqual(element("partyScreen").value as? String, partyId, "same party")

        app.navigationBars.buttons.firstMatch.tap()
        signInFresh("annie")
        XCTAssertTrue(element("joinBanner").waitForExistence(timeout: 6), "Join banner from hosted")
        element("joinBanner").buttons["Join"].tap()
        XCTAssertTrue(element("joinConfirmationScreen").waitForExistence(timeout: 4))
        let options = app.buttons.matching(identifier: "codeOption")
        XCTAssertTrue(options.firstMatch.waitForExistence(timeout: 4))
        options.matching(NSPredicate(format: "label != %@", code)).firstMatch.tap()
        XCTAssertTrue(element("joinCodeError").waitForExistence(timeout: 4), "wrong attempt accepted by rules")
        Thread.sleep(forTimeInterval: 1)
        options[code].tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: 6))
        XCTAssertEqual(element("partyScreen").value as? String, partyId)
        let twoMembers = NSPredicate { _, _ in self.seats.count == 2 }
        expectation(for: twoMembers, evaluatedWith: nil)
        waitForExpectations(timeout: 4)
        print("HOSTED members \(seats.allElementsBoundByIndex.map(\.label))")
    }

    func test2HostSeesExpiryThenStartsANewParty() {
        app.launch()
        signInFresh("maxi")
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: 6), "relaunch/sign-in opens the open party")
        print("HOSTED host sees members \(seats.allElementsBoundByIndex.map(\.label))")
        XCTAssertTrue(app.staticTexts["This party has expired"].waitForExistence(timeout: 330))
        app.buttons["backToHomeButton"].tap()
        XCTAssertTrue(app.buttons["startPartyButton"].waitForExistence(timeout: 4), "card back, no Back-to-party")
        Thread.sleep(forTimeInterval: 2)
        tapStartWhenSettled()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: 6), "new party right after expiry")
        print("HOSTED new party \(element("partyScreen").value as? String ?? "?") code \(element("joinCode").label)")
    }

    private var seats: XCUIElementQuery { app.descendants(matching: .any).matching(identifier: "memberSeat") }
    private func element(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id].firstMatch }

    private func signInFresh(_ username: String) {
        let field = app.textFields["usernameField"]
        if !field.waitForExistence(timeout: 4) {
            if app.navigationBars.buttons.firstMatch.exists { app.navigationBars.buttons.firstMatch.tap() }
            element("bottomBar").buttons["Me"].tap()
            app.buttons["Log out"].tap()
            XCTAssertTrue(field.waitForExistence(timeout: 4))
        }
        field.tap()
        field.typeText(username)
        app.buttons["Sign in"].tap()
    }

    private func tapStartWhenSettled() {
        let button = app.buttons["startPartyButton"]
        XCTAssertTrue(button.waitForExistence(timeout: 6))
        Thread.sleep(forTimeInterval: 1.5)
        button.tap()
    }
}
