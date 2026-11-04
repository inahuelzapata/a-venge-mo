import SwiftUI

/// Palette and shapes sampled from the reference screenshots in
/// docs/design/ (home-feed-reference-1.jpg / -2.jpg), listed in docs/design/README.md.
/// The Android shell currently uses different values; aligning the two apps is
/// still open (chiron-memory/contradictions.md).
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

    // Aven-mo mark and the centre Pay/Request button. Android uses the same
    // numbers in dp; all are in docs/design/visual-conventions.md ("Brand mark").

    /// Navy of the mark artwork (brand/aven-mo-mark.svg).
    static let markNavy = Color(hex: 0x021F40)
    /// Logo slot above the title on the login screen.
    static let loginMarkSize: CGFloat = 72
    /// Mark to the left of the "Home" title.
    static let headerMarkSize: CGFloat = 24
    static let headerMarkSpacing: CGFloat = 6
    /// Tab bar items: 6 + 52 icon slot + 3 + 13 label + 6 = 80, inside 6 padding (bar 92).
    static let tabIconSlot: CGFloat = 52
    static let payButtonDiameter: CGFloat = 60
    static let payButtonMarkSize: CGFloat = 30

    // "Split the check" card on Home, measured from the Kalshi card in the Venmo
    // reference. Android uses the same numbers in dp; all are in
    // docs/design/visual-conventions.md ("Split the check card").

    /// Card edge to the banner image, on the top and both sides.
    static let bannerImageInset: CGFloat = 4
    /// Concentric with the card's 28 corners (28 - 4).
    static let bannerImageCornerRadius: CGFloat = 24
    static let bannerImageToButtonSpacing: CGFloat = 12
    /// Added to `bannerImageInset`, so the button sits 18 from the card edge.
    static let bannerButtonSideInset: CGFloat = 14
    static let bannerButtonBottomInset: CGFloat = 16
    static let bannerButtonHeight: CGFloat = 38
    static let bannerButtonFontSize: CGFloat = 15
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
