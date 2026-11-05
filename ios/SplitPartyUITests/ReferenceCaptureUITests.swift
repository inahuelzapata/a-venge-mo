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

    // MARK: Total, tip and share (names match Android's TotalScreenshotTest)

    private static let tonysReceipt = [
        "TONY'S", "Greenwich Ave", "2 Margherita\t$36.00", "1 Caesar\t$12.50",
        "Subtotal\t$48.50", "Tax 6.35%\t$3.08", "TOTAL\t$51.58",
    ].joined(separator: "|")

    func test10TotalWaitingAndAdd() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie"])
        signIn(as: "annie")
        XCTAssertTrue(app.staticTexts["Waiting for the total"].waitForExistence(timeout: Self.timeout))
        scrollToBottom()
        try capture("total-waiting")

        app.terminate()
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie"])
        signIn(as: "maxi")
        XCTAssertTrue(element("addTotalCard").waitForExistence(timeout: Self.timeout))
        scrollToBottom()
        measure("total card", element("addTotalCard"))
        measure("scan button", app.buttons["scanReceiptButton"])
        try capture("total-add")

        app.buttons["enterTotalButton"].tap()
        XCTAssertTrue(app.textFields["amountField"].waitForExistence(timeout: Self.timeout))
        app.textFields["amountField"].typeText("48.50")
        measure("amount field", app.textFields["amountField"])
        measure("confirm button", app.buttons["confirmAmountButton"])
        try capture("total-keypad")
    }

    func test11Scan() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers", "annie",
                                               "-UITestReceiptEnv", Self.tonysReceipt, "-UITestScanHold", "4"])
        signIn(as: "maxi")
        XCTAssertTrue(app.buttons["scanReceiptButton"].waitForExistence(timeout: Self.timeout))
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(element("scanStatus").waitForExistence(timeout: Self.timeout))
        try capture("total-scanning", settling: false)
        XCTAssertTrue(element("confirmTotalScreen").waitForExistence(timeout: 8))
        try capture("total-confirm")

        app.terminate()
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestReceiptEnv", "blank"])
        signIn(as: "maxi")
        XCTAssertTrue(app.buttons["scanReceiptButton"].waitForExistence(timeout: Self.timeout))
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(element("entryNotice").waitForExistence(timeout: 8))
        try capture("total-no-total-found")

        app.terminate()
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestCameraDenied", "YES"])
        signIn(as: "maxi")
        XCTAssertTrue(app.buttons["scanReceiptButton"].waitForExistence(timeout: Self.timeout))
        app.buttons["scanReceiptButton"].tap()
        XCTAssertTrue(element("cameraUnavailable").waitForExistence(timeout: Self.timeout))
        try capture("total-camera-denied")
    }

    func test12TipAndShares() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers",
                                               "annie,christophe", "-UITestFriendPartyBill", "10000",
                                               "-UITestFriendPartyTip", "18"])
        signIn(as: "maxi")
        XCTAssertTrue(element("tipChips").waitForExistence(timeout: Self.timeout))
        scrollToBottom()
        measure("share summary", element("shareSummary"))
        measure("tip chips", element("tipChips"))
        measure("tip chip 18", app.buttons["tipChip-18%"])
        try capture("total-tip-chosen")

        for (count, members) in [(1, ""), (2, "annie"), (3, "annie,christophe")] {
            app.terminate()
            var extra = ["-UITestFriendParty", "maxi", "-UITestFriendPartyBill", "10000",
                         "-UITestFriendPartyTip", "18"]
            if !members.isEmpty { extra += ["-UITestFriendPartyMembers", members] }
            launch(resettingSession: true, extra: extra)
            // A member's view (host for the one-member party): own share, total, tip, people.
            signIn(as: count == 1 ? "maxi" : "annie")
            XCTAssertTrue(element("shareSummary").waitForExistence(timeout: Self.timeout))
            scrollToBottom()
            try capture("total-share-\(count)")
        }
    }

    func test13RemovedAndLeave() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers",
                                               "annie,christophe", "-UITestPartyScript", "4:remove=christophe"])
        signIn(as: "christophe")
        XCTAssertTrue(element("homeNotice").waitForExistence(timeout: 10))
        try capture("removed-message")

        app.terminate()
        launch(resettingSession: true, extra: ["-UITestFriendParty", "maxi", "-UITestFriendPartyMembers",
                                               "annie,christophe", "-UITestFriendPartyBill", "10000",
                                               "-UITestFriendPartyTip", "18"])
        signIn(as: "annie")
        let leave = app.buttons["leavePartyButton"]
        XCTAssertTrue(leave.waitForExistence(timeout: Self.timeout))
        scrollToBottom()
        leave.tap()
        XCTAssertTrue(app.buttons["confirmLeaveParty"].firstMatch.waitForExistence(timeout: Self.timeout))
        try capture("leave-confirm")
    }

    // MARK: Pay and settlement

    private static let paidPartyArguments = ["-UITestFriendParty", "annie", "-UITestFriendPartyMembers",
                                             "maxi,christophe", "-UITestFriendPartyBill", "10000",
                                             "-UITestFriendPartyTip", "18"]

    func test14Pay() throws {
        launch(resettingSession: true, extra: ["-UITestFriendParty", "annie", "-UITestFriendPartyMembers",
                                               "maxi,christophe"])
        signIn(as: "annie")
        XCTAssertTrue(app.buttons["payButton"].waitForExistence(timeout: Self.timeout))
        scrollToBottom()
        try capture("pay-add-total")

        app.terminate()
        launch(resettingSession: true, extra: Self.paidPartyArguments + ["-UITestWriteDelay", "6"])
        signIn(as: "annie")
        let pay = app.buttons["payButton"]
        XCTAssertTrue(pay.waitForExistence(timeout: Self.timeout))
        scrollToBottom()
        measure("pay button", pay)
        try capture("pay-ready")
        pay.tap()
        Thread.sleep(forTimeInterval: 0.6)
        try capture("pay-pending", settling: false)
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: 10))
        // Mid-burst: the confetti lasts about 2.6 s.
        Thread.sleep(forTimeInterval: 0.5)
        try capture("pay-confetti", settling: false)
        Thread.sleep(forTimeInterval: 3)
        measure("success card", element("settlementSuccess"))
        try capture("pay-success-host")

        app.terminate()
        launch(resettingSession: true, extra: Self.paidPartyArguments + ["-UITestPartyScript", "3:pay=host"])
        signIn(as: "christophe")
        XCTAssertTrue(element("settlementSuccess").waitForExistence(timeout: 8))
        Thread.sleep(forTimeInterval: 3)
        try capture("pay-success-member")

        // Maxi on Home while Annie settles with Christophe: the payment drops in, highlighted.
        app.terminate()
        launch(resettingSession: true, extra: ["-UITestFriendParty", "annie", "-UITestFriendPartyMembers",
                                               "christophe", "-UITestFriendPartyBill", "10000",
                                               "-UITestFriendPartyTip", "18", "-UITestPartyScript", "4:pay=host"])
        signIn(as: "maxi")
        let newRow = app.descendants(matching: .any).matching(identifier: "transactionRow").firstMatch
        let arrived = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS 'Christophe paid'"),
                                                object: newRow)
        XCTAssertEqual(XCTWaiter().wait(for: [arrived], timeout: 10), .completed)
        try capture("home-paid-highlight", settling: false)
    }

    func test15DemoReset() throws {
        launch(resettingSession: true, extra: ["-UITestWriteDelay", "4"])
        signIn(as: "maxi")
        XCTAssertTrue(element("splitPartyBanner").waitForExistence(timeout: Self.timeout))
        tapTab("Me")
        let reset = element("demoResetButton")
        XCTAssertTrue(reset.waitForExistence(timeout: Self.timeout))
        // All the way down: "hittable" is already true behind the floating tab bar.
        for _ in 0..<4 { element("meScreen").swipeUp() }
        measure("demo section", element("demoSection"))
        try capture("me-demo-section")
        reset.tap()
        XCTAssertTrue(app.buttons["confirmDemoReset"].firstMatch.waitForExistence(timeout: Self.timeout))
        try capture("demo-reset-confirm")
        app.buttons["confirmDemoReset"].firstMatch.tap()
        Thread.sleep(forTimeInterval: 0.6)
        try capture("demo-reset-progress", settling: false)

        app.terminate()
        launch(resettingSession: true, extra: ["-UITestDemoReset", "fail", "-UITestWriteDelay", "0.3"])
        signIn(as: "maxi")
        XCTAssertTrue(element("splitPartyBanner").waitForExistence(timeout: Self.timeout))
        tapTab("Me")
        XCTAssertTrue(reset.waitForExistence(timeout: Self.timeout))
        // All the way down: "hittable" is already true behind the floating tab bar.
        for _ in 0..<4 { element("meScreen").swipeUp() }
        reset.tap()
        app.buttons["confirmDemoReset"].firstMatch.tap()
        XCTAssertTrue(element("demoResetError").waitForExistence(timeout: Self.timeout))
        try capture("demo-reset-error")
    }

    // MARK: Helpers

    private func scrollToBottom() {
        element("partyScreen").swipeUp()
    }

    private var seats: XCUIElementQuery {
        app.descendants(matching: .any).matching(identifier: "memberSeat")
    }

    private var usernameField: XCUIElement { app.textFields["usernameField"] }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func launch(scenario: String = "seeded", resettingSession: Bool,
                        insertingIncomingPaymentAfter delay: TimeInterval? = nil, extra: [String] = []) {
        app.launchArguments = applyReceiptEnvironment(
            ["-UITestFixture", scenario, "-UITestResetSession", resettingSession ? "YES" : "NO"] + extra, to: app)
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
