import SwiftUI

/// A person's bubble for a `users.avatar` value (docs/firestore-schema.md,
/// docs/design/visual-conventions.md): their photo, square-cropped and clipped to
/// the circle, over coloured initials that show while the photo loads or whenever
/// it can't be shown. Values that aren't a photo draw initials on grey.
///
/// Photos come from the `avatarImageStore` environment value; without one
/// (previews, snapshot tests) the bubble draws initials only.
struct AvatarView: View {
    let avatarKey: String
    let displayName: String
    var size: CGFloat = 40

    @Environment(\.avatarImageStore) private var photoStore

    var body: some View {
        let spec = AvatarPhotos.spec(for: avatarKey)
        AvatarBubble(spec: spec, photo: photoStore?.cachedPhoto(for: spec), displayName: displayName, size: size)
            .task(id: spec.url) {
                guard let photoStore, let url = spec.url else { return }
                await photoStore.keepLoadingPhoto(url: url, crop: spec.crop)
            }
    }

    /// "Maxi" → "M", "Julie Wang" → "JW", "" → "?".
    static func initials(of displayName: String) -> String {
        let letters = displayName.split(separator: " ").prefix(2).compactMap(\.first)
        return letters.isEmpty ? "?" : String(letters).uppercased()
    }
}

/// The drawing itself, with the photo already resolved, so the inline feed image
/// (rendered outside the view tree, where the environment isn't available) and the
/// live view look identical.
private struct AvatarBubble: View {
    let spec: AvatarSpec
    let photo: UIImage?
    let displayName: String
    let size: CGFloat

    /// Initials are 42% of the circle, semibold.
    private static let initialsScale: CGFloat = 0.42

    var body: some View {
        ZStack {
            Circle().fill(spec.initialsColor)
            if let photo {
                // The stored photo is already square, so fill = fit: no stretching.
                Image(uiImage: photo)
                    .resizable()
                    .scaledToFill()
                    .accessibilityIdentifier("avatarPhoto")
            } else {
                Text(AvatarView.initials(of: displayName))
                    .font(.system(size: size * Self.initialsScale, weight: .semibold))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}

extension AvatarImageStore {
    /// The spec's photo if it is already on the phone; nil for initials-only values.
    func cachedPhoto(for spec: AvatarSpec) -> UIImage? {
        spec.url.flatMap { cachedPhoto(url: $0, crop: spec.crop) }
    }
}

extension AvatarView {
    /// The avatar as an image, so it can sit inline inside a `Text` sentence like
    /// the reference feed row ("(A) Annie paid (M) Maxi for …"). Cached because
    /// the feed asks for the same few avatars on every redraw. `photo` is passed
    /// in (from `AvatarImageStore`); the caller starts loading it.
    @MainActor
    static func inlineImage(avatarKey: String, displayName: String, size: CGFloat, photo: UIImage?) -> Image {
        // Photo or not is part of the key, so a row drawn with initials picks up the photo once it arrives.
        let cacheKey = "\(avatarKey)|\(displayName)|\(size)|\(photo == nil ? "initials" : "photo")"
        if let cached = InlineAvatarCache.images[cacheKey] { return cached }
        let bubble = AvatarBubble(spec: AvatarPhotos.spec(for: avatarKey), photo: photo,
                                  displayName: displayName, size: size)
        let renderer = ImageRenderer(content: bubble)
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
