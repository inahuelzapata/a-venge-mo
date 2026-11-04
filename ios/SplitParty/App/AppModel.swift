import Foundation
import Observation

/// Top-level app state: which database we talk to and who is signed in.
@MainActor
@Observable
final class AppModel {
    /// What the login footer says about the database, e.g. "Emulator 192.168.1.20:8080 …".
    let targetSummary: String
    /// Nil when setup is incomplete; `setupProblem` then says why.
    let repository: (any PaymentDataSource)?
    let setupProblem: String?

    private(set) var signedInUserId: String?
    /// Live listeners for the signed-in session. Recreated on every sign-in so a
    /// new user never sees the previous user's Me tab for a moment.
    private(set) var liveData: LiveDataStore?

    @ObservationIgnored private let sessionStore: SessionStore

    /// The app's real setup: the Firestore target from Info.plist, unless a Debug
    /// build was launched by the UI tests with a fixture (see `UITestLaunch`).
    convenience init() {
        #if DEBUG
        if let launch = UITestLaunch.fromProcessArguments() {
            let sessionStore = SessionStore()
            if launch.resetsSession { sessionStore.clear() }
            let fixture = FixtureDataSource(scenario: launch.scenario)
            if let delay = launch.insertsIncomingPaymentAfter {
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(delay))
                    fixture.insert(FixtureDataSource.incomingPartyPayment())
                }
            }
            self.init(dataSource: fixture, targetSummary: "UI test fixture (\(launch.scenario.rawValue))",
                      sessionStore: sessionStore)
            return
        }
        #endif
        let target = FirestoreTarget.fromBundle()
        switch FirebaseBootstrap.configure(target: target) {
        case let .ready(firestore):
            self.init(dataSource: PaymentRepository(firestore: firestore), targetSummary: target.summary)
        case let .failed(reason):
            self.init(dataSource: nil, targetSummary: target.summary, setupProblem: reason)
        }
    }

    /// `dataSource` nil means setup failed and `setupProblem` says why.
    init(dataSource: (any PaymentDataSource)?, targetSummary: String, setupProblem: String? = nil,
         sessionStore: SessionStore = SessionStore()) {
        self.repository = dataSource
        self.targetSummary = targetSummary
        self.setupProblem = setupProblem
        self.sessionStore = sessionStore
        // Stay signed in across restarts: reopen straight on Home for the saved user.
        if let dataSource, let savedUserId = sessionStore.signedInUserId {
            startSession(userId: savedUserId, repository: dataSource)
        }
    }

    /// Returns a message for the inline error, or nil on success.
    func signIn(username: String) async -> String? {
        guard let repository else { return setupProblem }
        do {
            let account = try await repository.signIn(username: username)
            sessionStore.save(userId: account.id)
            startSession(userId: account.id, repository: repository)
            return nil
        } catch LoginError.unknownUsername {
            return "No account called \"\(username.trimmingCharacters(in: .whitespaces))\". Try Maxi, Annie or Christophe."
        } catch {
            return "Can't reach the database. Check your connection and try again."
        }
    }

    func logOut() {
        sessionStore.clear()
        liveData?.stop()
        liveData = nil
        signedInUserId = nil
    }

    private func startSession(userId: String, repository: any PaymentDataSource) {
        liveData?.stop()
        signedInUserId = userId
        liveData = LiveDataStore(userId: userId, repository: repository)
    }
}

#if DEBUG
/// Launch arguments the UI tests pass (SplitPartyUITests). Debug builds only, so a
/// Release build can never be switched away from the real database.
///
/// - `-UITestFixture seeded|empty|unreachable`: use `FixtureDataSource` instead of Firestore.
/// - `-UITestResetSession YES`: start signed out.
/// - `-UITestInsertIncomingPaymentAfter <seconds>`: a new payment arrives after that delay.
struct UITestLaunch {
    static let fixtureKey = "UITestFixture"
    static let resetSessionKey = "UITestResetSession"
    static let insertIncomingPaymentAfterKey = "UITestInsertIncomingPaymentAfter"

    let scenario: FixtureDataSource.Scenario
    let resetsSession: Bool
    let insertsIncomingPaymentAfter: Double?

    /// "-key value" launch arguments land in UserDefaults' argument domain, which
    /// is never written to disk.
    static func fromProcessArguments(_ defaults: UserDefaults = .standard) -> UITestLaunch? {
        guard let rawScenario = defaults.string(forKey: fixtureKey),
              let scenario = FixtureDataSource.Scenario(rawValue: rawScenario) else { return nil }
        let delay = defaults.object(forKey: insertIncomingPaymentAfterKey) == nil
            ? nil : defaults.double(forKey: insertIncomingPaymentAfterKey)
        return UITestLaunch(scenario: scenario,
                            resetsSession: defaults.bool(forKey: resetSessionKey),
                            insertsIncomingPaymentAfter: delay)
    }
}
#endif
