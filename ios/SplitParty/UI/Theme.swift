import SwiftUI

/// Palette and shapes sampled from the reference screenshots in
/// docs/design/ (home-feed-reference-1.jpg / -2.jpg). Android uses the same hex
/// values (docs/design/README.md) so the two apps look the same.
enum Theme {
    /// Brand blue: the round action button in the reference bottom bar.
    static let brandBlue = Color(hex: 0x0087E0)
    /// Screen background behind the white cards.
    static let background = Color(hex: 0xF6F6F7)
    static let card = Color.white
    static let primaryText = Color.black
    /// Grey for connective words ("paid", "for") and timestamps.
    static let secondaryText = Color(hex: 0x7F7F7F)
    /// Light grey of the round like/comment buttons.
    static let actionBackground = Color(hex: 0xF1F1F3)
    static let errorText = Color(hex: 0xD92D20)
    /// Fading highlight on a feed row that just arrived.
    static let newRowHighlight = Color(hex: 0x0087E0).opacity(0.14)

    static let cardCornerRadius: CGFloat = 28
    static let cardPadding: CGFloat = 20
    static let screenGutter: CGFloat = 8
}

extension Color {
    /// `Color(hex: 0x0087E0)`, in sRGB.
    init(hex: UInt32) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }
}

/// The black full-width pill button of the reference ("Share", "Fund Now").
struct PillButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(Capsule().fill(Color.black.opacity(isEnabled ? 1 : 0.3)))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
