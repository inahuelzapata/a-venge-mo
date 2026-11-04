import Foundation

/// Remembers who is signed in on this phone, across app restarts, until Log out.
/// Only the user id (the username) is stored; everything else is read live.
struct SessionStore {
    private static let signedInUserIdKey = "signedInUserId"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var signedInUserId: String? {
        defaults.string(forKey: Self.signedInUserIdKey)
    }

    func save(userId: String) {
        defaults.set(userId, forKey: Self.signedInUserIdKey)
    }

    func clear() {
        defaults.removeObject(forKey: Self.signedInUserIdKey)
    }
}
