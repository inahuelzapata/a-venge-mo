import FirebaseFirestore
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

    /// How long a list may stay unconfirmed by the server before it is shown as
    /// unreachable. Long enough for a normal cold connect over Wi-Fi.
    private static let unreachableAfter: Duration = .seconds(4)
    /// Firestore ends a listener after an error; wait this long before reopening.
    private static let relistenDelay: Duration = .seconds(2)
    private static let highlightDuration: Duration = .seconds(1.5)

    @ObservationIgnored private let repository: PaymentRepository
    @ObservationIgnored private let connection = ConnectionMonitor()
    @ObservationIgnored private var registrations: [ListenerRegistration] = []
    @ObservationIgnored private var feedTracker = ListConnectionTracker()
    @ObservationIgnored private var myTransactionsTracker = ListConnectionTracker()
    @ObservationIgnored private var seenFeedIds: Set<String>?
    @ObservationIgnored private var isStopped = false

    init(userId: String, repository: PaymentRepository) {
        self.userId = userId
        self.repository = repository
        connection.whenNetworkRestored { [weak self] in
            self?.repository.reconnectNow()
        }
        startListening()
    }

    func stop() {
        isStopped = true
        registrations.forEach { $0.remove() }
        registrations = []
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
        registrations.append(repository.listenToUsers { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(usersById):
                self.usersById = usersById
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
        registrations.append(repository.listenToFeed { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(list):
                self.highlightNewRows(in: list.items)
                self.apply(list, to: \.feed, tracker: \.feedTracker)
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
        registrations.append(repository.listenToTransactions(involving: userId) { [weak self] result in
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

    private func highlightNewRows(in items: [PaymentTransaction]) {
        let currentIds = Set(items.map(\.id))
        defer { seenFeedIds = currentIds }
        // The first delivery is the initial list, not news.
        guard let seenFeedIds else { return }
        let newIds = currentIds.subtracting(seenFeedIds)
        guard !newIds.isEmpty else { return }
        highlightedTransactionIds.formUnion(newIds)
        Task { [weak self] in
            try? await Task.sleep(for: Self.highlightDuration)
            withAnimation(.easeOut(duration: 1)) {
                self?.highlightedTransactionIds.subtract(newIds)
            }
        }
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
