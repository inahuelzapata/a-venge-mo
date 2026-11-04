import XCTest

/// End-to-end flows of the app shell, driven through the UI. The app runs on its
/// built-in fixture data (`-UITestFixture`, Debug builds only), so no Firestore
/// emulator is needed and the data is always the seeded set.
final class AppShellUITests: XCTestCase {
    /// The work order's bar for sign-in and screen changes.
    private static let screenTimeout: TimeInterval = 2
    /// The incoming payment is injected this long after launch; the test waits a bit more.
    private static let incomingPaymentDelay: TimeInterval = 4
    private static let incomingPaymentTimeout: TimeInterval = 8

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: Login

    func testSeededUsernameSignsInToHome() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout), "Home should open within 2 s")
        XCTAssertEqual(transactionRows.count, 6, "All six seeded payments are listed")
        XCTAssertTrue(transactionRows.firstMatch.label.contains("Maxi paid"), "Newest seeded payment is first")
    }

    func testUnknownUsernameShowsInlineErrorAndStaysOnLogin() {
        launch(resettingSession: true)
        signIn(as: "thor")
        XCTAssertTrue(element("loginError").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(usernameField.exists, "Still on the login screen")
        XCTAssertFalse(homeScreen.exists)
    }

    func testEmptyUsernameCannotSubmit() {
        launch(resettingSession: true)
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(signInButton.isEnabled, "Empty field")
        usernameField.tap()
        usernameField.typeText("   ")
        XCTAssertFalse(signInButton.isEnabled, "Only spaces")
    }

    // MARK: Session

    func testSessionSurvivesRelaunchUntilLogOut() {
        launch(resettingSession: true)
        signIn(as: "Annie")  // any case works
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))

        app.terminate()
        launch(resettingSession: false)
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout), "Relaunch opens on Home")
        tapTab("Me")
        XCTAssertTrue(element("balance").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(element("balance").label, "$180.50", "Still signed in as Annie")

        app.buttons["Log out"].tap()
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout), "Log out returns to login")

        app.terminate()
        launch(resettingSession: false)
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout), "Next launch shows login")
    }

    // MARK: Shell

    func testBottomBarSwitchesTabsAndStaysVisible() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))

        tapTab("Pay or request")
        XCTAssertTrue(element("payRequestScreen").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(element("bottomBar").exists, "Bar stays visible on Pay/Request")

        tapTab("Me")
        XCTAssertTrue(element("meScreen").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(element("balance").label, "$250.00")
        XCTAssertEqual(transactionRows.count, 4, "Me lists only Maxi's own payments")

        tapTab("Home")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
    }

    func testIncomingPaymentAppearsAtTopOfFeed() {
        launch(resettingSession: true, insertingIncomingPaymentAfter: Self.incomingPaymentDelay)
        signIn(as: "maxi")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))

        let topRowIsIncoming = NSPredicate { _, _ in
            self.transactionRows.firstMatch.label.contains("$31.15")
        }
        XCTAssertTrue(bannerCard.waitForExistence(timeout: Self.screenTimeout), "Card slides in")
        let cardFrameBefore = bannerCard.frame
        expectation(for: topRowIsIncoming, evaluatedWith: nil)
        waitForExpectations(timeout: Self.incomingPaymentTimeout)
        XCTAssertEqual(transactionRows.count, 7)
        XCTAssertEqual(bannerCard.frame, cardFrameBefore, "The card does not move when a row arrives")
        assertBannerCardIsAbove(transactionRows.firstMatch)
    }

    func testEmptyFeedShowsEmptyState() {
        launch(scenario: "empty", resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(element("feedEmptyState").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(transactionRows.count, 0)
        assertBannerCardIsAbove(element("feedEmptyState"))
    }

    func testUnreachableDatabaseShowsErrorState() {
        // Sign in while the data is reachable, then relaunch with it unreachable.
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
        app.terminate()

        launch(scenario: "unreachable", resettingSession: false)
        XCTAssertTrue(element("feedErrorState").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertTrue(element("bottomBar").exists)
        assertBannerCardIsAbove(element("feedErrorState"))
    }

    // MARK: Split the check card

    func testBannerCardSitsAboveFeedWithAccessibleLabels() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(homeScreen.waitForExistence(timeout: Self.screenTimeout))
        assertBannerCardIsAbove(transactionRows.firstMatch)
        XCTAssertEqual(bannerImage.label,
                       "Split the check with friends. A faster, easier way to take care of the bill.",
                       "VoiceOver reads the banner's own copy, not an unnamed image")
        XCTAssertEqual(startPartyButton.label, "Start a party")
        XCTAssertEqual(bannerCard.buttons.count, 2, "Only the image and the button are tap targets")
    }

    func testStartPartyOpensThePartyScreenAndBackOffersTheWayBack() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(startPartyButton.waitForExistence(timeout: Self.screenTimeout))

        startPartyButton.tap()
        assertPartyScreenIsOpen()

        // Home now shows "Back to your party" where the card was.
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["backToPartyCard"].waitForExistence(timeout: Self.screenTimeout))
        XCTAssertFalse(partyScreen.exists)
    }

    func testBannerImageOpensThePartyScreen() {
        launch(resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(bannerImage.waitForExistence(timeout: Self.screenTimeout))
        bannerImage.tap()
        assertPartyScreenIsOpen()
    }

    // MARK: Helpers

    private var usernameField: XCUIElement { app.textFields["usernameField"] }
    private var signInButton: XCUIElement { app.buttons["Sign in"] }
    private var homeScreen: XCUIElement { element("homeScreen") }
    private var transactionRows: XCUIElementQuery {
        app.descendants(matching: .any).matching(identifier: "transactionRow")
    }

    private var bannerCard: XCUIElement { element("splitPartyBanner") }
    private var bannerImage: XCUIElement { app.buttons["splitPartyBannerImage"] }
    private var startPartyButton: XCUIElement { app.buttons["startPartyButton"] }
    private var partyScreen: XCUIElement { element("partyScreen") }

    private func assertBannerCardIsAbove(_ feedItem: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(bannerCard.waitForExistence(timeout: Self.screenTimeout), "Card shown", file: file, line: line)
        XCTAssertTrue(feedItem.exists, file: file, line: line)
        XCTAssertLessThanOrEqual(bannerCard.frame.maxY, feedItem.frame.minY,
                                 "Card sits above the feed", file: file, line: line)
    }

    private func assertPartyScreenIsOpen(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(partyScreen.waitForExistence(timeout: Self.screenTimeout), file: file, line: line)
        XCTAssertTrue(app.navigationBars["Split Party at Tony's"].exists, file: file, line: line)
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    /// Launch arguments understood by `UITestLaunch` in the app (AppModel.swift).
    private func launch(scenario: String = "seeded", resettingSession: Bool,
                        insertingIncomingPaymentAfter delay: TimeInterval? = nil) {
        app.launchArguments = ["-UITestFixture", scenario,
                               "-UITestResetSession", resettingSession ? "YES" : "NO"]
        if let delay {
            app.launchArguments += ["-UITestInsertIncomingPaymentAfter", String(delay)]
        }
        app.launch()
    }

    private func signIn(as username: String) {
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.screenTimeout))
        usernameField.tap()
        usernameField.typeText(username)
        signInButton.tap()
    }

    private func tapTab(_ title: String) {
        element("bottomBar").buttons[title].tap()
    }
}
