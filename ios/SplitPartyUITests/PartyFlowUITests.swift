import XCTest

/// Split Party flows (PoC-7 to PoC-9) on the built-in fixture: the demo switch,
/// starting a party, the Join banner, code confirmation, the live member list
/// and expiry. Fixture parties always use the code 🍕🍺🎉.
final class PartyFlowUITests: XCTestCase {
    /// The work order's bars: card within 2 s, banners and members within 1 s
    /// (plus a little for the UI test runner itself).
    private static let screenTimeout: TimeInterval = 2
    private static let liveTimeout: TimeInterval = 1.5

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: Demo switch

    func testDemoSwitchHidesAndShowsTheMerchantCardAndSurvivesRelaunch() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(merchantCard.waitForExistence(timeout: Self.screenTimeout), "Card arrives within 2 s")

        tapTab("Me")
        let toggle = demoToggle
        XCTAssertTrue(toggle.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(toggle.value as? String, "1", "On by default")
        toggle.switches.firstMatch.tap()
        XCTAssertEqual(toggle.value as? String, "0")

        tapTab("Home")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(merchantCard.waitForExistence(timeout: 2.5), "No card while the switch is off")
        let firstRow = transactionRows.firstMatch
        XCTAssertLessThan(firstRow.frame.minY, 0.3 * app.frame.height, "Feed moves up, no gap")

        app.terminate()
        launch(resettingSession: false)
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(merchantCard.waitForExistence(timeout: 2.5), "Still off after relaunch")
        tapTab("Me")
        XCTAssertEqual(demoToggle.value as? String, "0")
        demoToggle.switches.firstMatch.tap()

        tapTab("Home")
        XCTAssertTrue(merchantCard.waitForExistence(timeout: Self.screenTimeout), "Switching on brings it back")
        XCTAssertLessThanOrEqual(merchantCard.frame.maxY, transactionRows.firstMatch.frame.minY)
    }

    // MARK: Home banner slot

    func testFriendsPartyDropsAJoinBannerInPlaceOfTheMerchantCard() {
        // Counted from launch; sign-in typing takes a few seconds of that.
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyAfter", "10"])
        signIn(as: "annie")
        XCTAssertTrue(merchantCard.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(joinBanner.exists)
        XCTAssertTrue(joinBanner.waitForExistence(timeout: 10), "Join banner drops in")
        XCTAssertFalse(merchantCard.exists, "It replaces the merchant card")
        XCTAssertTrue(app.staticTexts["Maxi started a Split Party at Tony's"].exists)
        XCTAssertTrue(joinBanner.buttons["Join"].exists)
        XCTAssertLessThanOrEqual(joinBanner.frame.maxY, transactionRows.firstMatch.frame.minY)
    }

    func testJoinBannerVanishesWhenThePartyExpiresAndTheCardReturns() {
        // Created 290 s ago: expires about 10 s after launch.
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyAge", "290"])
        signIn(as: "christophe")
        XCTAssertTrue(joinBanner.waitForExistence(timeout: Self.screenTimeout))
        let gone = NSPredicate(format: "exists == false")
        expectation(for: gone, evaluatedWith: joinBanner)
        waitForExpectations(timeout: 10 + Self.liveTimeout)
        XCTAssertTrue(merchantCard.waitForExistence(timeout: Self.screenTimeout), "Demo switch is on: card returns")
    }

    func testMemberSeesBackToYourPartyAndRelaunchOpensTheParty() {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie"])
        signIn(as: "annie")
        // Signing in while already in an open party opens it, like a relaunch.
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(backToPartyCard.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(joinBanner.exists)
        XCTAssertFalse(merchantCard.exists)
        backToPartyCard.tap()
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))

        app.terminate()
        launch(resettingSession: false,
               extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie"])
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout), "Relaunch lands on the party")
    }

    func testStartAPartyTwiceReturnsToTheSameParty() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(startPartyButton.waitForExistence(timeout: Self.screenTimeout))
        startPartyButton.tap()
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))
        let firstPartyId = partyScreen.value as? String
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(backToPartyCard.waitForExistence(timeout: Self.screenTimeout), "Home offers the way back")
        XCTAssertFalse(merchantCard.exists)
        backToPartyCard.tap()
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(partyScreen.value as? String, firstPartyId, "Same party")
    }

    func testStartAPartyFailureShowsInlineErrorAndStaysOnHome() {
        // Sign in while reachable, then relaunch with every read and write failing.
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
        app.terminate()
        launch(scenario: "unreachable", resettingSession: false)
        XCTAssertTrue(startPartyButton.waitForExistence(timeout: Self.screenTimeout))
        startPartyButton.tap()
        XCTAssertTrue(element("startPartyError").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(partyScreen.exists)
        XCTAssertTrue(merchantCard.exists)
    }

    // MARK: Party screen

    func testHostSeesCodeCountdownSeatsAndFriendsArriveLive() {
        launch(resettingSession: true, extra: ["-UITestFriendsJoinEvery", "3"])
        signIn(as: "maxi")
        XCTAssertTrue(startPartyButton.waitForExistence(timeout: Self.screenTimeout))
        startPartyButton.tap()
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(app.navigationBars["Split Party at Tony's"].exists)
        XCTAssertEqual(element("joinCode").label, "Join code: pizza, beer, party popper")
        let countdown = element("partyCountdown").label
        XCTAssertTrue(countdown == "Expires in 5:00" || countdown == "Expires in 4:59", countdown)
        XCTAssertEqual(memberSeats.count, 1)
        XCTAssertEqual(memberSeats.firstMatch.label, "Maxi, host")
        XCTAssertEqual(emptySeats.count, 2)
        XCTAssertTrue(app.staticTexts["Waiting for the total"].exists)
        XCTAssertFalse(app.buttons["Pay"].exists)

        // Annie, then Christophe, join from "other phones".
        XCTAssertTrue(memberSeats.element(boundBy: 1).waitForExistence(timeout: 3 + Self.liveTimeout))
        XCTAssertTrue(memberSeats.element(boundBy: 2).waitForExistence(timeout: 3 + Self.liveTimeout))
        XCTAssertEqual(memberSeats.allElementsBoundByIndex.map(\.label), ["Maxi, host", "Annie", "Christophe"],
                       "Join order, host first")
        XCTAssertEqual(emptySeats.count, 0)
    }

    func testPartyScreenSwitchesToExpiredAtExpiry() {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie",
                                               "-UITestFriendPartyAge", "288"])
        signIn(as: "annie")
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(app.staticTexts["This party has expired"].waitForExistence(timeout: 12 + Self.liveTimeout))
        app.buttons["backToHomeButton"].tap()
        XCTAssertTrue(merchantCard.waitForExistence(timeout: Self.screenTimeout), "No Back-to-party card any more")
        XCTAssertFalse(backToPartyCard.exists)
    }

    // MARK: Code confirmation

    func testCorrectCodeJoinsAndLandsOnThePartyScreen() {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi"])
        signIn(as: "annie")
        XCTAssertTrue(joinBanner.waitForExistence(timeout: Self.screenTimeout))
        joinBanner.buttons["Join"].tap()
        XCTAssertTrue(confirmationScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(app.staticTexts["Join Maxi's party at Tony's"].exists, "Says whose party")
        assertFourOptionsWithExactlyOneRealCode()

        correctOption.tap()
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(memberSeats.element(boundBy: 1).waitForExistence(timeout: Self.liveTimeout))
        XCTAssertEqual(memberSeats.allElementsBoundByIndex.map(\.label), ["Maxi, host", "Annie"])
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(backToPartyCard.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(joinBanner.exists)
    }

    func testThreeWrongPicksLockThePartyAndHideItsJoinBanner() {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi"])
        signIn(as: "christophe")
        XCTAssertTrue(joinBanner.waitForExistence(timeout: Self.screenTimeout))
        joinBanner.buttons["Join"].tap()
        XCTAssertTrue(confirmationScreen.waitForExistence(timeout: Self.screenTimeout))

        for attempt in 1...2 {
            let orderBefore = optionLabels
            wrongOption.tap()
            XCTAssertTrue(element("joinCodeError").waitForExistence(timeout: Self.screenTimeout))
            XCTAssertTrue(element("joinCodeError").label.contains("\(3 - attempt) tr"), element("joinCodeError").label)
            let reshuffled = NSPredicate { _, _ in self.optionLabels != orderBefore }
            expectation(for: reshuffled, evaluatedWith: nil)
            waitForExpectations(timeout: Self.screenTimeout)
            assertFourOptionsWithExactlyOneRealCode()
        }
        wrongOption.tap()
        XCTAssertTrue(app.staticTexts["Too many wrong codes"].waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(codeOptions.count, 0)

        app.buttons["backToHomeButton"].tap()
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(merchantCard.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(joinBanner.exists, "No Join banner for a locked party")
    }

    // MARK: Helpers

    private var usernameField: XCUIElement { app.textFields["usernameField"] }
    private var homeScreen: XCUIElement { element("homeScreen") }
    private var merchantCard: XCUIElement { element("splitPartyBanner") }
    private var demoToggle: XCUIElement { element("demoModeToggle") }
    private var startPartyButton: XCUIElement { app.buttons["startPartyButton"] }
    private var joinBanner: XCUIElement { element("joinBanner") }
    private var backToPartyCard: XCUIElement { app.buttons["backToPartyCard"] }
    private var partyScreen: XCUIElement { element("partyScreen") }
    private var confirmationScreen: XCUIElement { element("joinConfirmationScreen") }
    private var codeOptions: XCUIElementQuery { app.buttons.matching(identifier: "codeOption") }
    private var optionLabels: [String] { codeOptions.allElementsBoundByIndex.map(\.label) }
    private static let fixtureCodeSpoken = "pizza, beer, party popper"
    private var correctOption: XCUIElement { codeOptions[Self.fixtureCodeSpoken] }
    private var wrongOption: XCUIElement {
        codeOptions.matching(NSPredicate(format: "label != %@", Self.fixtureCodeSpoken)).firstMatch
    }
    private var memberSeats: XCUIElementQuery { app.descendants(matching: .any).matching(identifier: "memberSeat") }
    private var emptySeats: XCUIElementQuery { app.descendants(matching: .any).matching(identifier: "emptySeat") }
    private var transactionRows: XCUIElementQuery {
        app.descendants(matching: .any).matching(identifier: "transactionRow")
    }

    private func assertFourOptionsWithExactlyOneRealCode(file: StaticString = #filePath, line: UInt = #line) {
        let labels = optionLabels
        XCTAssertEqual(labels.count, 4, file: file, line: line)
        XCTAssertEqual(Set(labels).count, 4, "All different", file: file, line: line)
        XCTAssertEqual(labels.filter { $0 == Self.fixtureCodeSpoken }.count, 1, file: file, line: line)
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    /// Launch arguments understood by `UITestLaunch` (AppModel.swift).
    private func launch(scenario: String = "seeded", resettingSession: Bool, extra: [String] = []) {
        app.launchArguments = ["-UITestFixture", scenario,
                               "-UITestResetSession", resettingSession ? "YES" : "NO"] + extra
        app.launch()
    }

    private func signIn(as username: String) {
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout))
        usernameField.tap()
        usernameField.typeText(username)
        app.buttons["Sign in"].tap()
    }

    private func tapTab(_ title: String) {
        element("bottomBar").buttons[title].tap()
    }
}
