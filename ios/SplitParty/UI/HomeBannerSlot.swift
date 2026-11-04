import SwiftUI

/// Space above the feed for banners (PoC-7/8). Shows, most important first:
/// "Back to your party" while the user is in an open party, the Join banner for a
/// friend's open party, the merchant card while the demo switch is on, or
/// nothing at all (then the feed moves up; no empty gap).
/// Measured values: docs/design/visual-conventions.md ("Home banner slot").
struct HomeBannerSlot: View {
    let onStartParty: () -> Void
    let onOpenParty: (String) -> Void
    let onJoin: (String) -> Void

    @Environment(LiveDataStore.self) private var liveData
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let hub = liveData.parties
        // A Group, not a stack: with nothing to show the slot adds no view and no
        // spacing, so the feed sits where the card was. Changes arrive inside the
        // hub's withAnimation (Motion.animate), which drives these transitions.
        Group {
            switch hub.banner {
            case let .backToParty(party):
                BackToPartyCard(merchantName: hub.merchantName, party: party, now: hub.now,
                                onOpen: { onOpenParty(party.id) })
                    .transition(dropTransition)
            case let .join(party):
                JoinBanner(hostName: liveData.usersById[party.hostId]?.firstName ?? party.hostId,
                           merchantName: hub.merchantName, onJoin: { onJoin(party.id) })
                    .id(party.id)
                    .transition(dropTransition)
            case .merchantCard:
                MerchantCardWithShimmer(shimmers: hub.shouldShimmerMerchantCard && !reduceMotion,
                                        isStarting: hub.isStartingParty, onStartParty: onStartParty)
                    .transition(slideTransition)
            case .none:
                EmptyView()
            }
            if let message = hub.startPartyError {
                StartPartyErrorRow(message: message, onDismiss: hub.dismissStartPartyError)
            }
        }
    }

    /// The Join and "Back to your party" banners drop in from above.
    private var dropTransition: AnyTransition {
        reduceMotion ? .identity : .move(edge: .top).combined(with: .opacity)
    }

    /// The merchant card slides in from the side, like a notification arriving.
    private var slideTransition: AnyTransition {
        reduceMotion ? .identity : .move(edge: .trailing).combined(with: .opacity)
    }
}

// MARK: Merchant card

/// The untouched "Split the check" card, with one light sweep across it when
/// it has just slid in.
private struct MerchantCardWithShimmer: View {
    let shimmers: Bool
    let isStarting: Bool
    let onStartParty: () -> Void

    @State private var shimmerProgress: CGFloat = -1

    var body: some View {
        SplitPartyBannerCard(onStartParty: onStartParty)
            .overlay {
                if shimmers {
                    ShimmerSweep(progress: shimmerProgress)
                        .padding(.horizontal, Theme.screenGutter)
                        .allowsHitTesting(false)
                }
            }
            .overlay {
                if isStarting {
                    ProgressView()
                        .controlSize(.large)
                        .tint(.white)
                        .accessibilityLabel("Starting your party")
                }
            }
            .disabled(isStarting)
            .onAppear {
                guard shimmers else { return }
                withAnimation(.easeInOut(duration: Theme.shimmerDuration).delay(Theme.shimmerDelay)) {
                    shimmerProgress = 1
                }
            }
    }
}

/// A diagonal white band crossing the card from left (`progress` -1) to right (1).
private struct ShimmerSweep: View {
    let progress: CGFloat

    var body: some View {
        GeometryReader { geometry in
            let bandWidth = geometry.size.width * Theme.shimmerBandWidthFraction
            LinearGradient(colors: [.white.opacity(0), .white.opacity(Theme.shimmerPeakOpacity), .white.opacity(0)],
                           startPoint: .leading, endPoint: .trailing)
                .frame(width: bandWidth)
                .rotationEffect(.degrees(Theme.shimmerAngleDegrees))
                .frame(height: geometry.size.height * 2)
                // progress -1 puts the band just left of the card, 1 just right of it.
                .offset(x: -bandWidth + (geometry.size.width + bandWidth) * (progress + 1) / 2,
                        y: -geometry.size.height / 2)
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous))
        .accessibilityHidden(true)
    }
}

// MARK: Join banner

/// A friend started a party here: one sentence and one Join action, with a
/// pulsing live dot. VoiceOver: "Maxi started a Split Party at Tony's", then "Join".
struct JoinBanner: View {
    let hostName: String
    let merchantName: String
    let onJoin: () -> Void

    var sentence: String { "\(hostName) started a Split Party at \(merchantName)" }

    var body: some View {
        HStack(spacing: Theme.joinBannerSpacing) {
            PulsingDot()
            Text(sentence)
                .font(.system(size: Theme.joinBannerFontSize, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            Button("Join", action: onJoin)
                .buttonStyle(SmallPillButtonStyle())
                .accessibilityIdentifier("joinPartyButton")
        }
        .padding(.horizontal, Theme.joinBannerHorizontalPadding)
        .padding(.vertical, Theme.joinBannerVerticalPadding)
        .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous).fill(Theme.card))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous)
            .stroke(Theme.brandBlue.opacity(0.25), lineWidth: 1))
        .padding(.horizontal, Theme.screenGutter)
        // VoiceOver reads the sentence, then the "Join" button.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("joinBanner")
    }
}

/// A brand-blue dot with a ring that grows and fades, saying "live".
struct PulsingDot: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isPulsing = false

    var body: some View {
        Circle()
            .fill(Theme.brandBlue)
            .frame(width: Theme.pulsingDotSize, height: Theme.pulsingDotSize)
            .background(
                Circle()
                    .fill(Theme.brandBlue.opacity(isPulsing ? 0 : 0.4))
                    .scaleEffect(isPulsing ? 2.6 : 1)
            )
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeOut(duration: 1.2).repeatForever(autoreverses: false)) { isPulsing = true }
            }
            .accessibilityHidden(true)
    }
}

// MARK: Back to your party

/// Shown instead of the merchant card and Join banner while the user is in an
/// open party, so leaving the party screen never strands them.
private struct BackToPartyCard: View {
    let merchantName: String
    let party: SplitParty
    let now: Date
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: Theme.joinBannerSpacing) {
                Text(party.joinCode.emojis.joined())
                    .font(.system(size: Theme.backToPartyCodeFontSize))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Back to your party")
                        .font(.system(size: Theme.joinBannerFontSize, weight: .semibold))
                        .foregroundStyle(Theme.primaryText)
                    Text("Split Party at \(merchantName) · \(Countdown.text(seconds: party.secondsLeft(at: now))) left")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.secondaryText)
                        .monospacedDigit()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.secondaryText)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Theme.joinBannerHorizontalPadding)
            .padding(.vertical, Theme.joinBannerVerticalPadding)
            .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous).fill(Theme.card))
            .contentShape(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Theme.screenGutter)
        .accessibilityLabel("Back to your party, Split Party at \(merchantName)")
        .accessibilityIdentifier("backToPartyCard")
    }
}

// MARK: Error

private struct StartPartyErrorRow: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.circle.fill")
                .accessibilityHidden(true)
            Text(message)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
            }
            .accessibilityLabel("Dismiss")
        }
        .font(.system(size: 15))
        .foregroundStyle(Theme.errorText)
        .padding(.horizontal, Theme.screenGutter + 12)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("startPartyError")
    }
}

/// "4:59", "0:07".
enum Countdown {
    static func text(seconds: Int) -> String {
        String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

/// Black capsule for one inline action ("Join").
struct SmallPillButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: Theme.bannerButtonFontSize, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, Theme.joinButtonHorizontalPadding)
            .frame(minHeight: Theme.bannerButtonHeight)
            .background(Capsule().fill(Color.black))
            .opacity(configuration.isPressed ? 0.8 : 1)
            .contentShape(Capsule())
    }
}
