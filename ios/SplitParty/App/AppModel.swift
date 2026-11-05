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

    /// The "Demo mode: at Tony's" switch (Me tab), standing in for the geofence:
    /// while on, Home offers the merchant card. Persisted across restarts.
    var isDemoAtMerchant: Bool {
        didSet {
            demoModeStore.save(isAtMerchant: isDemoAtMerchant)
            liveData?.demoModeChanged(isAtMerchant: isDemoAtMerchant)
        }
    }

    @ObservationIgnored private let sessionStore: SessionStore
    @ObservationIgnored private let demoModeStore: DemoModeStore

    /// The app's real setup: the Firestore target from Info.plist, unless a Debug
    /// build was launched by the UI tests with a fixture (see `UITestLaunch`).
    convenience init() {
        #if DEBUG
        if let launch = UITestLaunch.fromProcessArguments() {
            let sessionStore = SessionStore()
            if launch.resetsSession {
                sessionStore.clear()
                DemoModeStore().reset()
                LastPartyStore().partyId = nil
                CelebrationLedger().clear()
            }
            let fixture = FixtureDataSource(scenario: launch.scenario)
            fixture.demoResetFails = launch.demoResetFails
            if let delay = launch.writeDelaySeconds { fixture.writeDelay = .seconds(delay) }
            launch.applyPartySetup(to: fixture)
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
            self.init(dataSource: PaymentRepository(firestore: firestore, demoReset: DemoResetClient(target: target)),
                      targetSummary: target.summary)
        case let .failed(reason):
            self.init(dataSource: nil, targetSummary: target.summary, setupProblem: reason)
        }
    }

    /// `dataSource` nil means setup failed and `setupProblem` says why.
    init(dataSource: (any PaymentDataSource)?, targetSummary: String, setupProblem: String? = nil,
         sessionStore: SessionStore = SessionStore(), demoModeStore: DemoModeStore = DemoModeStore()) {
        self.repository = dataSource
        self.targetSummary = targetSummary
        self.setupProblem = setupProblem
        self.sessionStore = sessionStore
        self.demoModeStore = demoModeStore
        self.isDemoAtMerchant = demoModeStore.isAtMerchant
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
        LastPartyStore().partyId = nil
        liveData?.stop()
        liveData = nil
        signedInUserId = nil
    }

    private func startSession(userId: String, repository: any PaymentDataSource) {
        liveData?.stop()
        signedInUserId = userId
        liveData = LiveDataStore(userId: userId, repository: repository, isDemoAtMerchant: isDemoAtMerchant)
    }
}

#if DEBUG
/// Launch arguments the UI tests pass (SplitPartyUITests). Debug builds only, so a
/// Release build can never be switched away from the real database.
///
/// - `-UITestFixture seeded|empty|unreachable`: use `FixtureDataSource` instead of Firestore.
/// - `-UITestResetSession YES`: start signed out.
/// - `-UITestInsertIncomingPaymentAfter <seconds>`: a new payment arrives after that delay.
/// - `-UITestFriendParty <hostId>`: that user hosts an open party at Tony's (code 🍕🍺🎉).
///   `-UITestFriendPartyMembers a,b` adds members after the host,
///   `-UITestFriendPartyAge <seconds>` makes it that old (295 expires 5 s in), and
///   `-UITestFriendPartyAfter <seconds>` makes it appear only after that delay.
/// - `-UITestFriendsJoinEvery <seconds>`: when the signed-in user starts a party,
///   the other seeded users join it one by one at this interval.
/// - `-UITestFriendPartyBill <cents>` and `-UITestFriendPartyTip 18|custom:700`
///   give that party a bill and tip from the start.
/// - `-UITestPartyScript "2:tip=20;4:join=christophe;6:remove=annie;8:bill=20000"`:
///   changes other phones make to that party, each at its second after sign-in
///   (typing the username takes a variable few seconds, so launch is no anchor).
///   "pay=host" settles the party as the host's phone would; "reset=demo" runs
///   Demo reset as if another phone had tapped it.
/// - `-UITestDemoReset fail`: the Demo reset function answers with an error.
/// - `-UITestWriteDelay <seconds>`: how long the fake Pay and Demo reset take.
///   (Receipt-scan arguments: `ReceiptScanTestLaunch` in ReceiptScanView.swift.)
struct UITestLaunch {
    static let fixtureKey = "UITestFixture"
    static let resetSessionKey = "UITestResetSession"
    static let insertIncomingPaymentAfterKey = "UITestInsertIncomingPaymentAfter"
    static let friendPartyHostKey = "UITestFriendParty"
    static let friendPartyMembersKey = "UITestFriendPartyMembers"
    static let friendPartyAgeKey = "UITestFriendPartyAge"
    static let friendPartyAfterKey = "UITestFriendPartyAfter"
    static let friendsJoinEveryKey = "UITestFriendsJoinEvery"
    static let friendPartyBillKey = "UITestFriendPartyBill"
    static let friendPartyTipKey = "UITestFriendPartyTip"
    static let partyScriptKey = "UITestPartyScript"
    static let demoResetKey = "UITestDemoReset"
    static let writeDelayKey = "UITestWriteDelay"

    let scenario: FixtureDataSource.Scenario
    let resetsSession: Bool
    let insertsIncomingPaymentAfter: Double?
    var friendPartyHostId: String?
    var friendPartyMemberIds: [String] = []
    var friendPartyAge: Double = 0
    var friendPartyDelay: Double?
    var friendsJoinInterval: Double?
    var friendPartyBillCents: Int?
    var friendPartyTip: TipChoice?
    var partyScript: [(seconds: Double, change: String)] = []
    var demoResetFails = false
    var writeDelaySeconds: Double?

    @MainActor
    func applyPartySetup(to fixture: FixtureDataSource) {
        fixture.friendsJoinInterval = friendsJoinInterval
        guard let hostId = friendPartyHostId else { return }
        let memberIds = [hostId] + friendPartyMemberIds
        let age = friendPartyAge
        let bill = friendPartyBillCents
        let tip = friendPartyTip
        let script = partyScript
        let addFriendParty = {
            let party = fixture.addParty(hostId: hostId, memberIds: memberIds,
                                         createdAt: Date().addingTimeInterval(-age), billCents: bill, tip: tip)
            guard !script.isEmpty else { return }
            fixture.afterSignIn = {
                Task { @MainActor in
                    let start = ContinuousClock.now
                    for step in script.sorted(by: { $0.seconds < $1.seconds }) {
                        try? await Task.sleep(until: start + .seconds(step.seconds))
                        try? await Self.apply(step.change, to: party.id, in: fixture)
                    }
                }
            }
        }
        // Without a delay the party must exist before the first parties snapshot,
        // so a signed-in member lands straight on it (as after a relaunch).
        guard let delay = friendPartyDelay else {
            addFriendParty()
            return
        }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(delay))
            addFriendParty()
        }
    }

    /// "tip=18", "tip=custom:700", "bill=20000", "join=christophe", "remove=annie", "pay=host".
    @MainActor
    private static func apply(_ change: String, to partyId: String, in fixture: FixtureDataSource) async throws {
        let parts = change.split(separator: "=", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return }
        switch parts[0] {
        case "tip": if let tip = parseTip(parts[1]) { try await fixture.setTip(partyId: partyId, tip: tip) }
        case "bill": if let cents = Int(parts[1]) { try await fixture.setBill(partyId: partyId, billCents: cents) }
        case "join": try await fixture.joinParty(id: partyId, userId: parts[1])
        case "remove": try await fixture.removeMember(partyId: partyId, memberId: parts[1])
        case "pay": try await fixture.settleParty(id: partyId)
        case "reset": try await fixture.resetDemo()
        default: break
        }
    }

    private static func parseTip(_ raw: String) -> TipChoice? {
        if raw.hasPrefix("custom:") { return Int(raw.dropFirst("custom:".count)).map { .custom(amountCents: $0) } }
        return Int(raw).map(TipChoice.percent)
    }

    /// "-key value" launch arguments land in UserDefaults' argument domain, which
    /// is never written to disk.
    static func fromProcessArguments(_ defaults: UserDefaults = .standard) -> UITestLaunch? {
        guard let rawScenario = defaults.string(forKey: fixtureKey),
              let scenario = FixtureDataSource.Scenario(rawValue: rawScenario) else { return nil }
        let delay = defaults.object(forKey: insertIncomingPaymentAfterKey) == nil
            ? nil : defaults.double(forKey: insertIncomingPaymentAfterKey)
        var launch = UITestLaunch(scenario: scenario,
                                  resetsSession: defaults.bool(forKey: resetSessionKey),
                                  insertsIncomingPaymentAfter: delay)
        launch.friendPartyHostId = defaults.string(forKey: friendPartyHostKey)
        launch.friendPartyMemberIds = defaults.string(forKey: friendPartyMembersKey)?
            .split(separator: ",").map(String.init) ?? []
        launch.friendPartyAge = defaults.double(forKey: friendPartyAgeKey)
        launch.friendPartyDelay = defaults.object(forKey: friendPartyAfterKey) == nil
            ? nil : defaults.double(forKey: friendPartyAfterKey)
        launch.friendsJoinInterval = defaults.object(forKey: friendsJoinEveryKey) == nil
            ? nil : defaults.double(forKey: friendsJoinEveryKey)
        launch.friendPartyBillCents = defaults.string(forKey: friendPartyBillKey).flatMap { Int($0) }
        launch.friendPartyTip = defaults.string(forKey: friendPartyTipKey).flatMap(parseTip)
        launch.demoResetFails = defaults.string(forKey: demoResetKey) == "fail"
        launch.writeDelaySeconds = defaults.object(forKey: writeDelayKey) == nil
            ? nil : defaults.double(forKey: writeDelayKey)
        launch.partyScript = (defaults.string(forKey: partyScriptKey) ?? "").split(separator: ";").compactMap { step in
            let parts = step.split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2, let seconds = Double(parts[0]) else { return nil }
            return (seconds, parts[1])
        }
        return launch
    }
}
#endif
