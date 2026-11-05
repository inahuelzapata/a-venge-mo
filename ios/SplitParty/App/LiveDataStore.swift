import Foundation
import Observation
import SwiftUI

/// What a live list screen should show.
enum ListState: Equatable {
    case loading
    case loaded([PaymentTransaction])
    case unreachable(message: String)
}

/// The signed-in session's live data: every user (names, avatars, balances), the
/// feed and the signed-in user's own transactions. Listeners stay open while the
/// session lasts, so Home and Me are already current when the user switches tabs.
@MainActor
@Observable
final class LiveDataStore {
    let userId: String

    private(set) var usersById: [String: UserAccount] = [:]
    private(set) var feed: ListState = .loading
    private(set) var myTransactions: ListState = .loading
    /// Rows that arrived while the app was open; they get a fading highlight.
    private(set) var highlightedTransactionIds: Set<String> = []

    var signedInUser: UserAccount? { usersById[userId] }

    /// Split Party state for this session (open parties, banner, clock).
    let parties: PartyHub

    /// How long a list may stay unconfirmed by the server before it is shown as
    /// unreachable. Long enough for a normal cold connect over Wi-Fi.
    private static let unreachableAfter: Duration = .seconds(4)
    /// Firestore ends a listener after an error; wait this long before reopening.
    private static let relistenDelay: Duration = .seconds(2)
    private static let highlightDuration: Duration = .seconds(1.5)
    /// Several payments landing together (one Pay) drop in one after another, this far apart.
    private static let dropInInterval: Duration = .milliseconds(250)

    @ObservationIgnored private let repository: any PaymentDataSource
    @ObservationIgnored private let connection: ConnectionMonitor
    @ObservationIgnored private var subscriptions: [LiveSubscription] = []
    @ObservationIgnored private var feedTracker = ListConnectionTracker()
    @ObservationIgnored private var myTransactionsTracker = ListConnectionTracker()
    @ObservationIgnored private var seenFeedIds: Set<String>?
    @ObservationIgnored private var dropInTask: Task<Void, Never>?
    @ObservationIgnored private var isStopped = false

    init(userId: String, repository: any PaymentDataSource, isDemoAtMerchant: Bool = true) {
        self.userId = userId
        self.repository = repository
        let connection = ConnectionMonitor()
        self.connection = connection
        parties = PartyHub(userId: userId, repository: repository, connection: connection,
                           isDemoAtMerchant: isDemoAtMerchant)
        connection.whenNetworkRestored { [weak self] in
            self?.repository.reconnectNow()
        }
        startListening()
    }

    /// The Me tab's demo switch moved.
    func demoModeChanged(isAtMerchant: Bool) {
        parties.demoModeChanged(isAtMerchant: isAtMerchant)
    }

    func stop() {
        isStopped = true
        dropInTask?.cancel()
        parties.stop()
        subscriptions.forEach { $0.cancel() }
        subscriptions = []
        feedTracker.cancelTimer()
        myTransactionsTracker.cancelTimer()
    }

    // MARK: Listening

    private func startListening() {
        listenToUsers()
        listenToFeed()
        listenToMyTransactions()
    }

    private func listenToUsers() {
        subscriptions.append(repository.listenToUsers { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(usersById):
                self.usersById = usersById
                let friendIds = Set(usersById[self.userId]?.friendIds ?? [])
                if friendIds != self.parties.friendIds { self.parties.friendIds = friendIds }
            case .failure:
                // Rows still render with ids as names; the lists show the error.
                self.relistenLater { $0.listenToUsers() }
            }
        })
    }

    private func listenToFeed() {
        feedTracker.armTimeout(after: Self.unreachableAfter) { [weak self] in
            self?.markUnreachableIfUnconfirmed(\.feed, tracker: \.feedTracker)
        }
        subscriptions.append(repository.listenToFeed { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(list):
                let newRows = self.highlightNewRows(in: list.items)
                self.announce(newRows)
                self.applyFeed(list, droppingIn: newRows)
            case .failure:
                self.feed = .unreachable(message: Self.unreachableMessage)
                self.relistenLater { $0.listenToFeed() }
            }
        })
    }

    private func listenToMyTransactions() {
        myTransactionsTracker.armTimeout(after: Self.unreachableAfter) { [weak self] in
            self?.markUnreachableIfUnconfirmed(\.myTransactions, tracker: \.myTransactionsTracker)
        }
        subscriptions.append(repository.listenToTransactions(involving: userId) { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(list):
                self.apply(list, to: \.myTransactions, tracker: \.myTransactionsTracker)
            case .failure:
                self.myTransactions = .unreachable(message: Self.unreachableMessage)
                self.relistenLater { $0.listenToMyTransactions() }
            }
        })
    }

    // MARK: State updates

    private static let unreachableMessage = "Can't reach the database right now. We'll reconnect automatically."
    private static let offlineMessage = "You're offline. The feed comes back as soon as you're connected."

    /// Server-confirmed rows are shown. Rows that only come from the local cache
    /// mean the database is not answering: offline is shown at once, anything
    /// else after `unreachableAfter`.
    private func apply(_ list: LiveList<PaymentTransaction>,
                       to state: ReferenceWritableKeyPath<LiveDataStore, ListState>,
                       tracker: ReferenceWritableKeyPath<LiveDataStore, ListConnectionTracker>) {
        if !list.isFromCache {
            self[keyPath: tracker].confirmWithServer()
            self[keyPath: state] = .loaded(list.items)
            return
        }
        self[keyPath: tracker].lostServerConfirmation()
        if !connection.isNetworkAvailable {
            self[keyPath: state] = .unreachable(message: Self.offlineMessage)
            return
        }
        self[keyPath: tracker].armTimeout(after: Self.unreachableAfter) { [weak self] in
            self?.markUnreachableIfUnconfirmed(state, tracker: tracker)
        }
    }

    private func markUnreachableIfUnconfirmed(_ state: ReferenceWritableKeyPath<LiveDataStore, ListState>,
                                              tracker: ReferenceWritableKeyPath<LiveDataStore, ListConnectionTracker>) {
        guard !isStopped, !self[keyPath: tracker].isServerConfirmed else { return }
        self[keyPath: state] = .unreachable(message: connection.isNetworkAvailable
                                            ? Self.unreachableMessage : Self.offlineMessage)
    }

    /// Returns the rows that are news (in feed order, newest first).
    @discardableResult
    private func highlightNewRows(in items: [PaymentTransaction]) -> [PaymentTransaction] {
        let currentIds = Set(items.map(\.id))
        defer { seenFeedIds = currentIds }
        // The first delivery is the initial list, not news.
        guard let seenFeedIds else { return [] }
        let newIds = currentIds.subtracting(seenFeedIds)
        guard !newIds.isEmpty else { return [] }
        highlightedTransactionIds.formUnion(newIds)
        Task { [weak self] in
            try? await Task.sleep(for: Self.highlightDuration)
            // Reduce Motion: the highlight still shows, then goes without a fade.
            Motion.animate(.easeOut(duration: 1)) {
                self?.highlightedTransactionIds.subtract(newIds)
            }
        }
        return items.filter { newIds.contains($0.id) }
    }

    /// One Pay writes every payment in the same instant. Rather than all of them
    /// popping in at once, the oldest drops onto the top of the feed first and
    /// each newer one lands above it (newest first, as the feed is ordered).
    /// With Reduce Motion, or a single new row, the list is applied as is.
    private func applyFeed(_ list: LiveList<PaymentTransaction>, droppingIn newRows: [PaymentTransaction]) {
        dropInTask?.cancel()
        guard newRows.count > 1, !list.isFromCache, !UIAccessibility.isReduceMotionEnabled else {
            apply(list, to: \.feed, tracker: \.feedTracker)
            return
        }
        dropInTask = Task { [weak self] in
            for hiddenCount in stride(from: newRows.count - 1, through: 0, by: -1) {
                guard let self, !Task.isCancelled else { return }
                let hiddenIds = Set(newRows.prefix(hiddenCount).map(\.id))
                let partial = LiveList(items: list.items.filter { !hiddenIds.contains($0.id) }, isFromCache: false)
                self.apply(partial, to: \.feed, tracker: \.feedTracker)
                if hiddenCount > 0 { try? await Task.sleep(for: Self.dropInInterval) }
            }
        }
    }

    /// VoiceOver reads each new payment: "Maxi paid Annie 39 dollars and 33
    /// cents for Tony's Split Party." One announcement, so none cuts another off.
    private func announce(_ newRows: [PaymentTransaction]) {
        guard !newRows.isEmpty, UIAccessibility.isVoiceOverRunning else { return }
        let sentences = newRows.reversed().map { row in
            let payer = usersById[row.payerId]?.displayName ?? row.payerId
            let payee = usersById[row.payeeId]?.displayName ?? row.payeeId
            let memo = row.memo.isEmpty ? "" : " for \(row.memo)"
            return "\(payer) paid \(payee) \(Money.spoken(cents: row.amountCents))\(memo)."
        }
        UIAccessibility.post(notification: .announcement, argument: sentences.joined(separator: " "))
    }

    private func relistenLater(_ reopen: @escaping (LiveDataStore) -> Void) {
        Task { [weak self] in
            try? await Task.sleep(for: Self.relistenDelay)
            guard let self, !self.isStopped else { return }
            reopen(self)
        }
    }
}

/// Tracks whether one list has been confirmed by the server, and the pending
/// "give up waiting" timer.
struct ListConnectionTracker {
    private(set) var isServerConfirmed = false
    private var timeoutTask: Task<Void, Never>?

    mutating func confirmWithServer() {
        isServerConfirmed = true
        cancelTimer()
    }

    mutating func lostServerConfirmation() {
        isServerConfirmed = false
    }

    @MainActor
    mutating func armTimeout(after delay: Duration, _ onTimeout: @escaping @MainActor () -> Void) {
        // Keep an already running timer: re-arming on every cached snapshot would
        // keep pushing the error state further away.
        guard timeoutTask == nil else { return }
        timeoutTask = Task { @MainActor in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            onTimeout()
        }
    }

    mutating func cancelTimer() {
        timeoutTask?.cancel()
        timeoutTask = nil
    }
}
