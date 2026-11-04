import Foundation

/// Username rules from docs/firestore-schema.md: `users/{username.trim().toLowerCase()}`,
/// matching `^[a-z0-9_]{3,20}$`.
enum Username {
    // `#/…/#` because bare `/…/` regex literals need Swift 6 language mode.
    private static let allowedPattern = #/^[a-z0-9_]{3,20}$/#

    /// The document id to look up, or nil if the input can never be a valid
    /// username (empty, too short, or contains characters such as "/" that would
    /// otherwise make an invalid document path).
    static func normalized(_ rawUsername: String) -> String? {
        let candidate = rawUsername.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard candidate.wholeMatch(of: allowedPattern) != nil else { return nil }
        return candidate
    }
}
