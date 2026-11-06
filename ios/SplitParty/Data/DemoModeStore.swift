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

/// Remembers the "Custom shares" switch on the Me tab across restarts. Off on a
/// fresh install. It only decides whether THIS phone shows the host the controls
/// to set custom amounts: it never changes what a party is worth, and every phone
/// shows a party's custom shares whatever its own switch says.
struct CustomSharesStore {
    private static let isEnabledKey = "customSharesEnabled"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var isEnabled: Bool {
        defaults.bool(forKey: Self.isEnabledKey)
    }

    func save(isEnabled: Bool) {
        defaults.set(isEnabled, forKey: Self.isEnabledKey)
    }

    func reset() {
        defaults.removeObject(forKey: Self.isEnabledKey)
    }
}

/// The party screen the user last had open, so a relaunch right after Pay
/// (or during it) can show the paid party instead of plain Home.
struct LastPartyStore {
    private static let partyIdKey = "lastPartyId"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var partyId: String? {
        get { defaults.string(forKey: Self.partyIdKey) }
        nonmutating set { defaults.set(newValue, forKey: Self.partyIdKey) }
    }
}
