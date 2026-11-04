import Foundation

/// Remembers the "Demo mode: at Tony's" switch on the Me tab across restarts.
/// The PoC has no real geofence: this switch stands in for "the phone is at
/// Tony's" (PoC-7). On by default, so a fresh install is ready for the demo.
struct DemoModeStore {
    private static let isAtMerchantKey = "demoModeIsAtMerchant"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var isAtMerchant: Bool {
        defaults.object(forKey: Self.isAtMerchantKey) as? Bool ?? true
    }

    func save(isAtMerchant: Bool) {
        defaults.set(isAtMerchant, forKey: Self.isAtMerchantKey)
    }

    func reset() {
        defaults.removeObject(forKey: Self.isAtMerchantKey)
    }
}
