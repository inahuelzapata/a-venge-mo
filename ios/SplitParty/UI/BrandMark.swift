import SwiftUI

/// The Aven-mo mark (Assets.xcassets/AvenMoMark, a copy of brand/aven-mo-mark.svg
/// kept as a vector so it stays sharp at every size). Its square box includes a
/// little room above and below the artwork; `size` is the box.
///
/// Decorative: hidden from VoiceOver. Where it is the only content of a control,
/// the control carries the label.
struct BrandMark: View {
    var size: CGFloat
    /// Fixed colours only, so the mark never changes with the system appearance.
    var color: Color = Theme.markNavy

    var body: some View {
        Image("AvenMoMark")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
