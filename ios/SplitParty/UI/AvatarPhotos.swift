import SwiftUI

/// Square crop of a photo, in fractions of the source image: the square's centre
/// (`centerX` of the width, `centerY` of the height) and its `side` as a fraction
/// of the image's shorter edge. Clamped to the image when applied. The seeded
/// people's numbers are shared with Android (docs/design/visual-conventions.md)
/// so both phones frame the faces the same way.
struct AvatarCrop: Hashable {
    let centerX: Double
    let centerY: Double
    let side: Double

    /// The largest centred square: used for photos that have no tuned crop.
    static let centered = AvatarCrop(centerX: 0.5, centerY: 0.5, side: 1)
}

/// What a `users.avatar` value draws: the photo to fetch (`url`, nil for initials
/// only), how to crop it, and the colour behind the initials shown while the photo
/// loads or whenever it can't be shown.
struct AvatarSpec: Equatable {
    let url: URL?
    let crop: AvatarCrop
    let initialsColor: Color
}

/// Avatar values the app understands (docs/firestore-schema.md,
/// docs/design/visual-conventions.md). Mirrors Android's `AvatarPhotos`.
enum AvatarPhotos {
    static let fallbackColor = Color(hex: 0x8E8E93)

    private struct SeededPerson {
        let key: String
        let url: String
        let crop: AvatarCrop
        let initialsColor: Color
    }

    private static func tallerPhoto(_ file: String) -> String {
        "https://taller.ai/_next/image?url=%2Fleadership%2F\(file).jpg&w=1080&q=75"
    }

    private static let seededPeople = [
        SeededPerson(key: "avatar_maxi", url: tallerPhoto("maximiliano-armesto"),
                     crop: AvatarCrop(centerX: 0.50, centerY: 0.40, side: 0.70), initialsColor: Color(hex: 0x0087E0)),
        SeededPerson(key: "avatar_annie", url: tallerPhoto("annie-rodriguez"),
                     crop: AvatarCrop(centerX: 0.55, centerY: 0.40, side: 0.70), initialsColor: Color(hex: 0xE5487D)),
        SeededPerson(key: "avatar_christophe", url: tallerPhoto("christophe-kolb"),
                     crop: AvatarCrop(centerX: 0.50, centerY: 0.35, side: 1.00), initialsColor: Color(hex: 0x2EAF6F)),
    ]

    /// Each seeded person is found by their photo URL (what the seed stores now)
    /// or their older `avatar_*` key (fixtures, old data).
    private static let seededPersonByAvatarValue: [String: SeededPerson] = Dictionary(
        uniqueKeysWithValues: seededPeople.flatMap { [($0.key, $0), ($0.url, $0)] }
    )

    /// A seeded URL or key resolves to that person's photo, crop and colour. Any
    /// other https URL is loaded with a centred crop over grey initials; anything
    /// else (missing, empty, unknown key, not an https URL) is grey initials only.
    static func spec(for avatarValue: String?) -> AvatarSpec {
        let trimmedValue = avatarValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if let person = seededPersonByAvatarValue[trimmedValue] {
            return AvatarSpec(url: URL(string: person.url), crop: person.crop, initialsColor: person.initialsColor)
        }
        return AvatarSpec(url: httpsURL(trimmedValue), crop: .centered, initialsColor: fallbackColor)
    }

    private static func httpsURL(_ value: String) -> URL? {
        guard let url = URL(string: value), url.scheme?.lowercased() == "https", url.host?.isEmpty == false else {
            return nil
        }
        return url
    }
}
