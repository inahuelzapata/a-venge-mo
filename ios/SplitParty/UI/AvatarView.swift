import SwiftUI

/// Draws a `users.avatar` key (docs/firestore-schema.md) as a coloured circle
/// with initials. No avatar art exists yet, so each seeded key maps to a fixed
/// colour that Android uses too (docs/design/README.md); unknown keys fall back to
/// grey initials, as the schema asks.
struct AvatarView: View {
    let avatarKey: String
    let displayName: String
    var size: CGFloat = 40

    private static let colorsByAvatarKey: [String: Color] = [
        "avatar_maxi": Color(hex: 0x0087E0),
        "avatar_annie": Color(hex: 0xE5487D),
        "avatar_christophe": Color(hex: 0x2EAF6F),
    ]
    private static let fallbackColor = Color(hex: 0x8E8E93)

    var body: some View {
        Circle()
            .fill(Self.colorsByAvatarKey[avatarKey] ?? Self.fallbackColor)
            .overlay(
                Text(Self.initials(of: displayName))
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(.white)
            )
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }

    /// "Maxi" → "M", "Julie Wang" → "JW", "" → "?".
    static func initials(of displayName: String) -> String {
        let letters = displayName.split(separator: " ").prefix(2).compactMap(\.first)
        return letters.isEmpty ? "?" : String(letters).uppercased()
    }
}

extension AvatarView {
    /// The avatar as an image, so it can sit inline inside a `Text` sentence like
    /// the reference feed row ("(A) Annie paid (M) Maxi for …"). Cached because
    /// the feed asks for the same few avatars on every redraw.
    @MainActor
    static func inlineImage(avatarKey: String, displayName: String, size: CGFloat) -> Image {
        let cacheKey = "\(avatarKey)|\(displayName)|\(size)"
        if let cached = InlineAvatarCache.images[cacheKey] { return cached }
        let renderer = ImageRenderer(content: AvatarView(avatarKey: avatarKey, displayName: displayName, size: size))
        renderer.scale = UIScreen.main.scale
        let image = renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "person.crop.circle")
        InlineAvatarCache.images[cacheKey] = image
        return image
    }
}

@MainActor
private enum InlineAvatarCache {
    static var images: [String: Image] = [:]
}
