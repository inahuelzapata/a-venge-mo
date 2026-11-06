import Foundation
import Observation
import SwiftUI

/// What Home's banner slot shows, most important first.
enum HomeBanner: Equatable {
    /// The signed-in user is in an open party: always offer the way back.
    case backToParty(SplitParty)
    /// A friend's open party the user can join.
    case join(SplitParty)
    /// The demo switch says we're at Tony's and nothing above applies.
    case merchantCard
    case none
}

/// Session-wide Split Party state (PoC-7 to PoC-9): the open parties at Tony's,
/// which one the user is in or may join, the shared clock that expires them, and
/// starting a party. Owned by `LiveDataStore`, so it lives as long as the session.
@MainActor
@Observable
final class PartyHub {
    let userId: String
    let merchantId = DemoMerchant.id

    /// Open by status; some may already be past `expiresAt` (see `now`).
    private(set) var openParties: [SplitParty] = []
    /// True once the parties query has answered at least once.
    private(set) var hasLoadedParties = false
    private(set) var merchantName = DemoMerchant.fallbackName
    /// The clock every party screen and banner reads, so they all flip at once.
    private(set) var now = Date()

    private(set) var isDemoAtMerchant: Bool
    /// The merchant card slides in once per session (and again after the demo
    /// switch is turned back on), standing in for the geofence match.
    private(set) var isMerchantCardRevealed = false
    /// When the reveal happened; the shimmer plays only right after it.
    private(set) var merchantCardRevealedAt: Date?

    private(set) var isStartingParty = false
    private(set) var startPartyError: String?
    /// Set once per session when the user is already in an open party at launch
    /// (relaunch or sign-in); Home's navigation opens it and clears this.
    var partyToOpenOnLaunch: String?

    /// A short message Home shows once, e.g. after the host removed this user.
    private(set) var homeNotice: String?
    @ObservationIgnored private var noticeTask: Task<Void, Never>?
    static let homeNoticeDuration: Duration = .seconds(5)

    /// The signed-in user's friends (from `users/{id}.friendIds`).
    var friendIds: Set<String> = []

    // `Repository` and `connection` are shared with the screens' own models.
    @ObservationIgnored let repository: any PaymentDataSource
    @ObservationIgnored let connection: ConnectionMonitor
    @ObservationIgnored private var subscriptions: [LiveSubscription] = []
    @ObservationIgnored private var clockTask: Task<Void, Never>?
    @ObservationIgnored private var hasCheckedLaunchParty = false
    /// Party id → when this phone last asked to expire it.
    @ObservationIgnored private var expiryRequests: [String: Date] = [:]
    @ObservationIgnored private var isStopped = false
    /// Which paid parties this phone already celebrated (ticks and confetti once).
    @ObservationIgnored let celebrations = CelebrationLedger()
    @ObservationIgnored private let lastPartyStore = LastPartyStore()
    @ObservationIgnored private var launchPartyCheck: LiveSubscription?
    /// A relaunch reopens a just-paid party only this soon after Pay.
    private static let reopenPaidPartyWindow: TimeInterval = 15 * 60

    /// The geofence stand-in: how long after the parties have loaded the card
    /// slides in, and the longest Home waits for them first.
    static let merchantCardRevealDelay: Duration = .milliseconds(350)
    static let partiesWaitBeforeReveal: Duration = .seconds(1.2)
    /// The shimmer plays only if Home shows the card this soon after the reveal.
    static let shimmerWindow: TimeInterval = 2
    private static let expiryRetryInterval: TimeInterval = 10
    private static let relistenDelay: Duration = .seconds(2)

    init(userId: String, repository: any PaymentDataSource, connection: ConnectionMonitor, isDemoAtMerchant: Bool) {
        self.userId = userId
        self.repository = repository
        self.connection = connection
        self.isDemoAtMerchant = isDemoAtMerchant
        listenToOpenParties()
        subscriptions.append(repository.listenToMerchantName(merchantId: merchantId) { [weak self] name in
            self?.merchantName = name
        })
        startClock()
    }

    func stop() {
        isStopped = true
        launchPartyCheck?.cancel()
        subscriptions.forEach { $0.cancel() }
        subscriptions = []
        clockTask?.cancel()
    }

    // MARK: Derived state

    /// The open, unexpired party the user belongs to (newest, if ever several).
    var myParty: SplitParty? {
        openParties.filter { $0.isActive(at: now) && $0.isMember(userId) }.max { $0.createdAt < $1.createdAt }
    }

    /// A friend's open, unexpired party the user is not in and not locked out of.
    var joinableParty: SplitParty? {
        openParties.filter {
            $0.isActive(at: now) && !$0.isMember(userId) && friendIds.contains($0.hostId) && !$0.isLockedOut(userId)
        }.max { $0.createdAt < $1.createdAt }
    }

    var banner: HomeBanner {
        if let myParty { return .backToParty(myParty) }
        if let joinableParty { return .join(joinableParty) }
        if isDemoAtMerchant && isMerchantCardRevealed { return .merchantCard }
        return .none
    }

    var shouldShimmerMerchantCard: Bool {
        guard let merchantCardRevealedAt else { return false }
        return Date().timeIntervalSince(merchantCardRevealedAt) < Self.shimmerWindow
    }

    // MARK: Demo switch and merchant card

    func demoModeChanged(isAtMerchant: Bool) {
        isDemoAtMerchant = isAtMerchant
        if !isAtMerchant {
            isMerchantCardRevealed = false
            merchantCardRevealedAt = nil
        }
    }

    /// Called while Home is on screen: waits for the parties to answer (so a
    /// Join banner is not preceded by a flash of the merchant card), then slides
    /// the card in.
    func revealMerchantCardWhenReady() async {
        guard isDemoAtMerchant, !isMerchantCardRevealed else { return }
        let waitStart = ContinuousClock.now
        while !hasLoadedParties && ContinuousClock.now - waitStart < Self.partiesWaitBeforeReveal {
            try? await Task.sleep(for: .milliseconds(50))
        }
        try? await Task.sleep(for: Self.merchantCardRevealDelay)
        guard !Task.isCancelled, isDemoAtMerchant, !isMerchantCardRevealed else { return }
        merchantCardRevealedAt = Date()
        Motion.animate(.spring(response: 0.5, dampingFraction: 0.85)) {
            isMerchantCardRevealed = true
        }
    }

    /// After a Demo reset the demo starts over: the card leaves without motion
    /// and slides in (with its shimmer) the next time Home is on screen.
    func replayMerchantCardEntrance() {
        isMerchantCardRevealed = false
        merchantCardRevealedAt = nil
    }

    /// Snapshot tests: the card is already there, with no slide or shimmer.
    func revealMerchantCardWithoutAnimation() {
        isMerchantCardRevealed = isDemoAtMerchant
        merchantCardRevealedAt = nil
    }

    // MARK: Start a party

    /// Returns the party to open: the one the user is already in, or a new one.
    /// Nil on failure, with `startPartyError` set for the inline error.
    func startParty() async -> String? {
        if let myParty { return myParty.id }
        guard !isStartingParty else { return nil }
        startPartyError = nil
        guard connection.isNetworkAvailable else {
            startPartyError = PartyActionError.offline.message
            return nil
        }
        isStartingParty = true
        defer { isStartingParty = false }
        do {
            let party = try await repository.startParty(hostId: userId, merchantId: merchantId)
            // The listener confirms it in a moment; show it now so Home already
            // offers "Back to your party" when the user comes back.
            if !openParties.contains(where: { $0.id == party.id }) {
                Motion.animate(.spring(response: 0.45, dampingFraction: 0.8)) { openParties.append(party) }
            }
            return party.id
        } catch {
            startPartyError = (error as? PartyActionError ?? .unreachable).message
            return nil
        }
    }

    func dismissStartPartyError() {
        startPartyError = nil
    }

    // MARK: Home notice

    func showHomeNotice(_ message: String) {
        noticeTask?.cancel()
        Motion.animate(.spring(response: 0.45, dampingFraction: 0.8)) { homeNotice = message }
        UIAccessibility.post(notification: .announcement, argument: message)
        noticeTask = Task { [weak self] in
            try? await Task.sleep(for: Self.homeNoticeDuration)
            guard !Task.isCancelled else { return }
            Motion.animate(.easeInOut(duration: 0.3)) { self?.homeNotice = nil }
        }
    }

    // MARK: Listening

    private func listenToOpenParties() {
        subscriptions.append(repository.listenToOpenParties(merchantId: merchantId) { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(list):
                Motion.animate(.spring(response: 0.45, dampingFraction: 0.8)) {
                    self.openParties = list.items
                }
                self.hasLoadedParties = true
                if !list.isFromCache { self.openLaunchPartyOnce() }
                self.expireDueHostedParties()
            case .failure:
                self.relistenLater()
            }
        })
    }

    private func relistenLater() {
        Task { [weak self] in
            try? await Task.sleep(for: Self.relistenDelay)
            guard let self, !self.isStopped else { return }
            self.listenToOpenParties()
        }
    }

    /// Relaunch mid-party lands straight back on the party screen. With no open
    /// party, the party screen the user last had open is reopened if it was paid
    /// or cancelled in the last few minutes, so a phone killed during Pay shows the
    /// paid state and one closed before the host cancelled shows the cancelled state.
    private func openLaunchPartyOnce() {
        guard !hasCheckedLaunchParty else { return }
        hasCheckedLaunchParty = true
        if let myParty {
            partyToOpenOnLaunch = myParty.id
            return
        }
        guard let lastPartyId = lastPartyStore.partyId else { return }
        launchPartyCheck = repository.listenToParty(id: lastPartyId) { [weak self] result in
            guard let self, case let .success(document) = result, !document.isFromCache else { return }
            self.launchPartyCheck?.cancel()
            self.launchPartyCheck = nil
            // Paid or cancelled while the app was closed: show how it ended, not Home.
            if let party = document.item, party.isMember(self.userId),
               let endedAt = party.status == .paid ? (party.paidAt ?? Date())
                   : party.isCancelled ? (party.cancelledAt ?? Date()) : nil,
               Date().timeIntervalSince(endedAt) < Self.reopenPaidPartyWindow {
                self.partyToOpenOnLaunch = party.id
            } else {
                self.lastPartyStore.partyId = nil
            }
        }
    }

    // MARK: Last party screen

    /// The party screen remembers itself so a relaunch can come back to it.
    func rememberLastParty(_ partyId: String) {
        lastPartyStore.partyId = partyId
    }

    /// The user left a finished party (Back to Home, removed, reset).
    func forgetLastParty() {
        lastPartyStore.partyId = nil
    }

    // MARK: Clock and expiry

    /// Ticks on every whole second (for the countdowns) and exactly at each open
    /// party's expiry, so every phone flips to "expired" by the clock.
    private func startClock() {
        clockTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let wait = self?.secondsUntilNextTick() else { return }
                try? await Task.sleep(for: .seconds(wait))
                guard let self, !Task.isCancelled else { return }
                Motion.animate(.easeInOut(duration: 0.3)) {
                    self.now = Date()
                }
                self.expireDueHostedParties()
            }
        }
    }

    private func secondsUntilNextTick() -> TimeInterval {
        let current = Date()
        let seconds = current.timeIntervalSince1970
        var wait = seconds.rounded(.up) - seconds
        if wait <= 0 { wait = 1 }
        for party in openParties where party.expiresAt > current {
            wait = min(wait, party.expiresAt.timeIntervalSince(current))
        }
        // A hair past the boundary, so `now >= expiresAt` holds when we wake.
        return wait + 0.005
    }

    /// The schema leaves writing "expired" to the host's phone. A failed write
    /// (typically: another phone did it first) is retried later at most, never shown.
    private func expireDueHostedParties() {
        let current = Date()
        for party in openParties where party.hostId == userId && party.isExpired(at: current) {
            if let lastRequest = expiryRequests[party.id],
               current.timeIntervalSince(lastRequest) < Self.expiryRetryInterval { continue }
            expiryRequests[party.id] = current
            Task { await repository.expireParty(id: party.id) }
        }
    }
}

/// Animations started from models, where SwiftUI's Reduce Motion environment
/// value is not available: with Reduce Motion on, the change is instant.
enum Motion {
    @MainActor
    static func animate(_ animation: Animation, _ change: () -> Void) {
        if UIAccessibility.isReduceMotionEnabled {
            change()
        } else {
            withAnimation(animation, change)
        }
    }
}
