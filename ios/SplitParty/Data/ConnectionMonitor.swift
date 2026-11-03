import Foundation
import Network
import Observation

/// Whether the phone has any network path (Wi-Fi, cellular), e.g. false in
/// airplane mode. Used together with Firestore's "from cache" flag to decide
/// when to show the error state, and to reconnect as soon as the network returns.
@MainActor
@Observable
final class ConnectionMonitor {
    private(set) var isNetworkAvailable = true

    @ObservationIgnored private let pathMonitor = NWPathMonitor()
    @ObservationIgnored private var onNetworkRestored: (() -> Void)?

    init() {
        pathMonitor.pathUpdateHandler = { [weak self] path in
            let isAvailable = path.status == .satisfied
            Task { @MainActor in self?.update(isAvailable: isAvailable) }
        }
        pathMonitor.start(queue: DispatchQueue(label: "ConnectionMonitor"))
    }

    deinit {
        pathMonitor.cancel()
    }

    func whenNetworkRestored(_ action: @escaping () -> Void) {
        onNetworkRestored = action
    }

    private func update(isAvailable: Bool) {
        let wasAvailable = isNetworkAvailable
        isNetworkAvailable = isAvailable
        if isAvailable && !wasAvailable {
            onNetworkRestored?()
        }
    }
}
