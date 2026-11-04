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
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.timeout))
        try capture("home-feed")

        app.buttons["startPartyButton"].tap()
        XCTAssertTrue(element("splitPartyPlaceholderScreen").waitForExistence(timeout: Self.timeout))
        try capture("split-party-placeholder")
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(element("homeScreen").waitForExistence(timeout: Self.timeout))

        tapTab("Pay or request")
        XCTAssertTrue(element("payRequestScreen").waitForExistence(timeout: Self.timeout))
        try capture("pay-request")

        tapTab("Me")
        XCTAssertTrue(element("meScreen").waitForExistence(timeout: Self.timeout))
        try capture("me")
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

    // MARK: Helpers

    private var usernameField: XCUIElement { app.textFields["usernameField"] }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func launch(scenario: String = "seeded", resettingSession: Bool,
                        insertingIncomingPaymentAfter delay: TimeInterval? = nil) {
        app.launchArguments = ["-UITestFixture", scenario,
                               "-UITestResetSession", resettingSession ? "YES" : "NO"]
        if let delay { app.launchArguments += ["-UITestInsertIncomingPaymentAfter", String(delay)] }
        app.launch()
    }

    private func signIn() {
        XCTAssertTrue(usernameField.waitForExistence(timeout: Self.timeout))
        usernameField.tap()
        usernameField.typeText("maxi")
        app.buttons["Sign in"].tap()
    }

    private func tapTab(_ title: String) {
        element("bottomBar").buttons[title].tap()
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
