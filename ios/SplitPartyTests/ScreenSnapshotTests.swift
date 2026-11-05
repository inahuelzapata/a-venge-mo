import SnapshotTesting
import SwiftUI
import XCTest
@testable import SplitParty

/// Pixel snapshots of every app-shell screen, compared with the reference PNGs in
/// `__Snapshots__/ScreenSnapshotTests/` (stored with Git LFS, see .gitattributes).
///
/// Re-record after an intended visual change:
///   TEST_RUNNER_SNAPSHOT_TESTING_RECORD=all xcodebuild test … -only-testing:SplitPartyTests/ScreenSnapshotTests
/// then review the changed PNGs before committing them.
@MainActor
final class ScreenSnapshotTests: XCTestCase {
    /// Text rendering differs between iOS versions, so references are only valid
    /// on the OS they were recorded with. Other simulators skip instead of failing.
    private static let referenceSystemVersion = "26.5"
    /// Fixed screen size and scale, independent of which iPhone simulator runs.
    private static let device = ViewImageConfig.iPhone13Pro(.portrait)
    /// Rendered in sRGB, not the simulator's Display P3. With P3 references the
    /// library's perceptual comparison misreads saturated colours (the brand blue),
    /// so any small difference elsewhere failed the whole screen.
    private static let traits = UITraitCollection(traitsFrom: [device.traits, UITraitCollection(displayGamut: .SRGB)])
    /// Every pixel must match, but "match" allows a 2 % perceptual difference, which
    /// absorbs anti-aliasing noise. A pixel-count allowance (precision < 1) was too
    /// loose: a changed brand blue passed on screens where blue covers under 1 %.
    private static let pixelPrecision: Float = 1
    private static let perceptualPrecision: Float = 0.98
    /// The error state's "Reconnecting…" spinner animates, so its frame differs on
    /// every run. It covers about 0.5 % of the screen; the blue icon and Pay button
    /// (about 1 %) stay above this allowance, so a colour change still fails.
    private static let animatedSpinnerPixelPrecision: Float = 0.995

    private static let emulatorSummary = "Emulator 192.168.1.20:8080 (project demo-split-party)"

    override func setUpWithError() throws {
        try XCTSkipUnless(UIDevice.current.systemVersion == Self.referenceSystemVersion,
                          "Snapshot references are recorded on iOS \(Self.referenceSystemVersion) "
                          + "(iPhone 17 Pro simulator); this is iOS \(UIDevice.current.systemVersion).")
    }

    // MARK: Login

    func testLoginEmpty() {
        let appModel = makeAppModel(scenario: .seeded, signedInUserId: nil)
        assertScreen(LoginView(focusesFieldOnAppear: false), appModel: appModel)
    }

    func testLoginUnknownUsername() {
        let appModel = makeAppModel(scenario: .seeded, signedInUserId: nil)
        let login = LoginView(initialUsername: "thor",
                              initialErrorMessage: "No account called \"thor\". Try Maxi, Annie or Christophe.",
                              focusesFieldOnAppear: false)
        assertScreen(login, appModel: appModel)
    }

    func testLoginNotConfigured() {
        let appModel = AppModel(dataSource: nil, targetSummary: "Firestore target not configured",
                                setupProblem: "FIREBASE_TARGET is not set. Copy ios/Config/FirestoreTarget.example.xcconfig "
                                    + "to FirestoreTarget.local.xcconfig.",
                                sessionStore: makeSessionStore(signedInUserId: nil))
        assertScreen(LoginView(focusesFieldOnAppear: false), appModel: appModel)
    }

    // MARK: Signed in

    func testHomeFeed() throws {
        try assertSignedInScreen(scenario: .seeded, tab: .home)
    }

    func testHomeEmpty() throws {
        try assertSignedInScreen(scenario: .empty, tab: .home)
    }

    func testHomeUnreachable() throws {
        try assertSignedInScreen(scenario: .unreachable, tab: .home,
                                 pixelPrecision: Self.animatedSpinnerPixelPrecision)
    }

    func testMe() throws {
        try assertSignedInScreen(scenario: .seeded, tab: .me)
    }

    func testPayRequestPlaceholder() throws {
        try assertSignedInScreen(scenario: .seeded, tab: .payRequest)
    }

    // MARK: Helpers

    private func assertSignedInScreen(scenario: FixtureDataSource.Scenario, tab: AppTab,
                                      homePath: [HomeDestination] = [],
                                      pixelPrecision: Float = ScreenSnapshotTests.pixelPrecision,
                                      file: StaticString = #filePath, testName: String = #function,
                                      line: UInt = #line) throws {
        let appModel = makeAppModel(scenario: scenario, signedInUserId: "maxi")
        let liveData = try XCTUnwrap(appModel.liveData, "Saved session should open as maxi")
        defer { liveData.stop() }
        // The merchant card normally slides in a moment after Home opens.
        liveData.parties.revealMerchantCardWithoutAnimation()
        assertScreen(MainTabView(initialTab: tab, initialHomePath: homePath).environment(liveData), appModel: appModel,
                     pixelPrecision: pixelPrecision, file: file, testName: testName, line: line)
    }

    private func assertScreen(_ screen: some View, appModel: AppModel,
                              pixelPrecision: Float = ScreenSnapshotTests.pixelPrecision,
                              file: StaticString = #filePath, testName: String = #function, line: UInt = #line) {
        let root = screen
            .environment(appModel)
            .preferredColorScheme(.light)
            // The app's background is drawn by each screen; this matches the window.
            .background(Theme.background)
        let controller = UIHostingController(rootView: root)
        // Drawn through the host app's real window: plain layer rendering skips the
        // bottom bar's material blur (it comes out see-through) and leaves hairline
        // artifacts at the edges of capsule shapes.
        assertSnapshot(of: controller,
                       as: .image(drawHierarchyInKeyWindow: true, precision: pixelPrecision,
                                  perceptualPrecision: Self.perceptualPrecision,
                                  size: Self.device.size, traits: Self.traits),
                       file: file, testName: testName, line: line)
    }

    private func makeAppModel(scenario: FixtureDataSource.Scenario, signedInUserId: String?) -> AppModel {
        AppModel(dataSource: FixtureDataSource(scenario: scenario), targetSummary: Self.emulatorSummary,
                 sessionStore: makeSessionStore(signedInUserId: signedInUserId),
                 demoModeStore: DemoModeStore(defaults: throwawayDefaults(suiteName: "ScreenSnapshotTests.demoMode")))
    }

    /// A throwaway defaults suite, so tests never touch the app's real saved session.
    private func makeSessionStore(signedInUserId: String?) -> SessionStore {
        let store = SessionStore(defaults: throwawayDefaults(suiteName: "ScreenSnapshotTests"))
        if let signedInUserId { store.save(userId: signedInUserId) }
        return store
    }

    private func throwawayDefaults(suiteName: String) -> UserDefaults {
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return defaults
    }
}
