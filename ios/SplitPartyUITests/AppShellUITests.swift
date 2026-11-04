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

        tapTab("Pay/Request")
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
        expectation(for: topRowIsIncoming, evaluatedWith: nil)
        waitForExpectations(timeout: Self.incomingPaymentTimeout)
        XCTAssertEqual(transactionRows.count, 7)
    }

    func testEmptyFeedShowsEmptyState() {
        launch(scenario: "empty", resettingSession: true)
        signIn(as: "maxi")
        XCTAssertTrue(element("feedEmptyState").waitForExistence(timeout: Self.screenTimeout))
        XCTAssertEqual(transactionRows.count, 0)
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
    }

    // MARK: Helpers

    private var usernameField: XCUIElement { app.textFields["usernameField"] }
    private var signInButton: XCUIElement { app.buttons["Sign in"] }
    private var homeScreen: XCUIElement { element("homeScreen") }
    private var transactionRows: XCUIElementQuery {
        app.descendants(matching: .any).matching(identifier: "transactionRow")
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
