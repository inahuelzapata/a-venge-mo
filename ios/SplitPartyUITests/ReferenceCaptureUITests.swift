import XCTest

/// Takes the visual reference captures in docs/design/ios-captures/ (the contract
/// the Android app is styled against; see docs/design/visual-conventions.md).
///
/// Skipped in normal test runs. To refresh the captures, run
/// `docs/design/capture/capture-ios.sh`, which sets `CAPTURE_DIR` for this test.
/// Uses the Debug fixture data (`-UITestFixture`), so every run shows the same rows.
/// The login field focuses itself on appear, so login captures show the system keyboard.
final class ReferenceCaptureUITests: XCTestCase {
    private static let timeout: TimeInterval = 5
    /// Lets fades, springs and the cursor settle before a capture.
    private static let settle: TimeInterval = 1.2

    private var app: XCUIApplication!
    private var captureDir: URL!

    override func setUpWithError() throws {
        let env = ProcessInfo.processInfo.environment
        guard let dir = env["CAPTURE_DIR"], !dir.isEmpty else {
            throw XCTSkip("Set CAPTURE_DIR to take the reference captures")
        }
        captureDir = URL(fileURLWithPath: dir)
        try FileManager.default.createDirectory(at: captureDir, withIntermediateDirectories: true)
        continueAfterFailure = false
        app = XCUIApplication()
    }

    func test01Login() throws {
        launch(resettingSession: true)
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.timeout))
        try capture("login-empty")

        usernameField.tap()
        usernameField.typeText("maxi")
        try capture("login-filled")

        usernameField.tap()
        usernameField.clearAndType("thor")
        app.buttons["Sign in"].tap()
        XCTAssertTrue(element("loginError").waitForExistence(timeout: Self.timeout))
        try capture("login-error")
    }

    func test02NotConfigured() throws {
        // No fixture and no local xcconfig: the real "not configured" launch.
        app.launchArguments = []
        app.launch()
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.timeout))
        try capture("login-not-configured")
    }

    func test03HomeTabs() throws {
        launch(resettingSession: true)
        signIn()
        XCTAssertTrue(element("splitPartyBanner").waitForExistence(timeout: Self.timeout))
        // After the slide-in and the shimmer sweep.
        Thread.sleep(forTimeInterval: 1.5)
        try capture("home-feed")

        tapTab("Pay or request")
        XCTAssertTrue(element("payRequestScreen").waitForExistence(timeout: Self.timeout))
        try capture("pay-request")

        tapTab("Me")
        XCTAssertTrue(element("meScreen").waitForExistence(timeout: Self.timeout))
        try capture("me")

        // Demo switch off: no merchant card, the feed moves up.
        element("demoModeToggle").switches.firstMatch.tap()
        tapTab("Home")
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.timeout))
        try capture("home-demo-off")
        tapTab("Me")
        element("demoModeToggle").switches.firstMatch.tap()
    }

    func test04HomeNewRowHighlight() throws {
        launch(resettingSession: true, insertingIncomingPaymentAfter: 5)
        signIn()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.timeout))
        let incoming = app.descendants(matching: .any).matching(identifier: "transactionRow").firstMatch
        let arrived = NSPredicate { _, _ in incoming.label.contains("$31.15") }
        expectation(for: arrived, evaluatedWith: nil)
        waitForExpectations(timeout: 10)
        // The highlight fades after 1.5 s; capture once the insert spring has settled.
        Thread.sleep(forTimeInterval: 0.45)
        try capture("home-new-row-highlight", settling: false)
    }

    func test05HomeEmpty() throws {
        launch(scenario: "empty", resettingSession: true)
        signIn()
        XCTAssertTrue(element("feedEmptyState").waitForExistence(timeout: Self.timeout))
        try capture("home-empty")
    }

    func test06HomeError() throws {
        launch(resettingSession: true)
        signIn()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.timeout))
        app.terminate()
        launch(scenario: "unreachable", resettingSession: false)
        XCTAssertTrue(element("feedErrorState").waitForExistence(timeout: Self.timeout))
        try capture("home-error")
    }

    // MARK: Split Party (fixture parties always use 🍕🍺🎉)

    func test07HostPartyFillsUp() throws {
        launch(resettingSession: true, extra: ["-UITestFriendsJoinEvery", "6"])
        signIn()
        XCTAssertTrue(app.buttons["startPartyButton"].waitForExistence(timeout: Self.timeout))
        app.buttons["startPartyButton"].tap()
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.timeout))
        try capture("party-one-member")
        XCTAssertTrue(seats.element(boundBy: 1).waitForExistence(timeout: 8))
        try capture("party-two-members")
        XCTAssertTrue(seats.element(boundBy: 2).waitForExistence(timeout: 8))
        try capture("party-three-members")
        measure("party code", element("joinCode"))
        measure("party countdown", element("partyCountdown"))
        measure("party member row", element("memberRow"))
        measure("party first seat", seats.firstMatch)
        measure("party total area", element("totalPlaceholder"))
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["backToPartyCard"].waitForExistence(timeout: Self.timeout))
        try capture("home-back-to-party")
        measure("back-to-party card", app.buttons["backToPartyCard"])
    }

    func test08JoinAndConfirm() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi"])
        signIn(as: "annie")
        XCTAssertTrue(element("joinBanner").waitForExistence(timeout: Self.timeout))
        try capture("join-banner")
        measure("join banner", element("joinBanner"))
        measure("join button", element("joinBanner").buttons["Join"])
        element("joinBanner").buttons["Join"].tap()
        XCTAssertTrue(element("joinConfirmationScreen").waitForExistence(timeout: Self.timeout))
        try capture("join-confirm")
        let options = app.buttons.matching(identifier: "codeOption")
        measure("first option", options.element(boundBy: 0))
        measure("second option", options.element(boundBy: 1))

        let wrong = app.buttons.matching(identifier: "codeOption")
            .matching(NSPredicate(format: "label != %@", "pizza, beer, party popper")).firstMatch
        wrong.tap()
        // Mid-shake, with the red outline and the error.
        Thread.sleep(forTimeInterval: 0.12)
        try capture("join-confirm-wrong-pick", settling: false)
        Thread.sleep(forTimeInterval: 1)
        for _ in 0..<2 {
            app.buttons.matching(identifier: "codeOption")
                .matching(NSPredicate(format: "label != %@", "pizza, beer, party popper")).firstMatch.tap()
            Thread.sleep(forTimeInterval: 1)
        }
        XCTAssertTrue(app.staticTexts["Too many wrong codes"].waitForExistence(timeout: Self.timeout))
        try capture("join-locked")
    }

    func test09Expired() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie",
                                               "-UITestFriendPartyAge", "290"])
        signIn(as: "annie")
        XCTAssertTrue(element("partyScreen").waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(app.staticTexts["This party has expired"].waitForExistence(timeout: 15))
        try capture("party-expired")
    }

    // MARK: Helpers

    private var seats: XCUIElementQuery {
        app.descendants(matching: .any).matching(identifier: "memberSeat")
    }

    private var usernameField: XCUIElement { app.textFields["usernameField"] }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func launch(scenario: String = "seeded", resettingSession: Bool,
                        insertingIncomingPaymentAfter delay: TimeInterval? = nil, extra: [String] = []) {
        app.launchArguments = ["-UITestFixture", scenario,
                               "-UITestResetSession", resettingSession ? "YES" : "NO"] + extra
        if let delay { app.launchArguments += ["-UITestInsertIncomingPaymentAfter", String(delay)] }
        app.launch()
    }

    private func signIn(as username: String = "maxi") {
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.timeout))
        usernameField.tap()
        usernameField.typeText(username)
        app.buttons["Sign in"].tap()
    }

    private func tapTab(_ title: String) {
        element("bottomBar").buttons[title].tap()
    }

    /// Prints an element's frame in points, for the measured values in
    /// docs/design/visual-conventions.md (grep the test log for "MEASURE").
    private func measure(_ name: String, _ element: XCUIElement) {
        let frame = element.frame
        print(String(format: "MEASURE %@: x %.1f y %.1f w %.1f h %.1f", name,
                     frame.minX, frame.minY, frame.width, frame.height))
    }

    private func capture(_ name: String, settling: Bool = true) throws {
        if settling { Thread.sleep(forTimeInterval: Self.settle) }
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try png.write(to: captureDir.appendingPathComponent("\(name).png"))
    }
}

private extension XCUIElement {
    func clearAndType(_ text: String) {
        let current = (value as? String) ?? ""
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count) + text)
    }
}
