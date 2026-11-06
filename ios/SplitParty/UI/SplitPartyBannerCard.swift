import SwiftUI
import UIKit

/// The "Split the check with friends" card at the top of the Home feed: the
/// entry point to Split Party (PRD FR-1.2, PoC-7).
///
/// Laid out like the Kalshi ad card in the Venmo reference (docs/design/), minus
/// its header row: a white rounded card holding the banner artwork and a
/// full-width black "Start a party" button. The artwork carries the copy and the
/// Tony's identity, so the card reads no Firestore data at all. It cannot
/// disappear or change when merchants, payments or the connection do, and rows
/// arriving below it never re-render it.
/// Measured values: docs/design/visual-conventions.md ("Split the check card").
struct SplitPartyBannerCard: View {
    /// Called when the hero image or the button is tapped.
    let onStartParty: () -> Void

    /// The words baked into the artwork, so VoiceOver reads what sighted people see.
    static let bannerCopy = "Tony's at the J House. Answer a trivia and get a 10% discount."
    static let buttonTitle = "Start a party"

    var body: some View {
        VStack(spacing: Theme.bannerImageToButtonSpacing) {
            Button(action: onStartParty) {
                BannerArtwork()
            }
            .buttonStyle(NoFeedbackButtonStyle())
            .accessibilityLabel(Self.bannerCopy)
            .accessibilityIdentifier("splitPartyBannerImage")

            Button(Self.buttonTitle, action: onStartParty)
                .buttonStyle(CompactPillButtonStyle())
                .padding(.horizontal, Theme.bannerButtonSideInset)
                .accessibilityIdentifier("startPartyButton")
        }
        .padding([.top, .horizontal], Theme.bannerImageInset)
        .padding(.bottom, Theme.bannerButtonBottomInset)
        .background(
            RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous)
                .fill(Theme.card)
        )
        .padding(.horizontal, Theme.screenGutter)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("splitPartyBanner")
    }
}

/// The banner image at its own aspect ratio, so none of its text is ever cropped
/// whatever the phone's width.
private struct BannerArtwork: View {
    /// `brand/banners/split-the-check-with-friends.png`, bundled straight from
    /// the shared brand folder (see the Xcode project's "Shared brand assets"
    /// group). Android's build reads the same file, so there is one copy only.
    /// Loaded once, so re-rendering Home never decodes it again.
    private static let image = UIImage(named: "split-the-check-with-friends.png")
    /// The supplied artwork is 1738 × 905 (about 1.92 : 1). Only used if the
    /// file is missing, so the placeholder takes the same space as the image.
    private static let fallbackAspectRatio: CGFloat = 1738.0 / 905.0

    var body: some View {
        Group {
            if let image = Self.image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            } else {
                // A missing file must not crash or collapse the card.
                Theme.background.aspectRatio(Self.fallbackAspectRatio, contentMode: .fit)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.bannerImageCornerRadius, style: .continuous))
        .contentShape(Rectangle())
    }
}

/// The card's black button: the reference's "Fund Now" pill, which is shorter
/// than the login's `PillButtonStyle`. Same press feedback (dims to 80%).
private struct CompactPillButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: Theme.bannerButtonFontSize, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: Theme.bannerButtonHeight)
            .background(Capsule().fill(Color.black))
            .opacity(configuration.isPressed ? 0.8 : 1)
            .contentShape(Capsule())
    }
}

/// Tapping the artwork navigates, but like rows and tabs it shows no flash.
private struct NoFeedbackButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
    }
}
