import SwiftUI

/// Home: header, the banner slot, then the live feed of everyone's payments,
/// newest first.
struct HomeView: View {
    /// Called from the banner slot (merchant card, Join banner, back-to-party).
    var onStartParty: () -> Void = {}
    var onOpenParty: (String) -> Void = { _ in }
    var onJoin: (String) -> Void = { _ in }

    @Environment(LiveDataStore.self) private var liveData

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HomeHeader(user: liveData.signedInUser, fallbackName: liveData.userId)
                HomeBannerSlot(onStartParty: onStartParty, onOpenParty: onOpenParty, onJoin: onJoin)
                TransactionList(state: liveData.feed,
                                emptyTitle: "No payments yet",
                                emptyMessage: "When friends pay each other, it shows up here.")
            }
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        // The geofence stand-in: while Home is on screen and the demo switch is
        // on, the merchant card arrives (once per session, or after switching on).
        .task(id: liveData.parties.isDemoAtMerchant) {
            await liveData.parties.revealMerchantCardWhenReady()
        }
        .accessibilityIdentifier("homeScreen")
    }
}

/// Like the reference header: the user's round avatar on the left and the
/// search / QR capsule on the right, with the screen title (and the Aven-mo mark
/// just left of it) in the middle.
/// Search and QR are decorative in the PoC.
private struct HomeHeader: View {
    let user: UserAccount?
    let fallbackName: String

    var body: some View {
        ZStack {
            Text("Home")
                .font(.system(size: 20, weight: .bold))
                // An overlay, so the title stays centred exactly where it was.
                .overlay(alignment: .leading) {
                    BrandMark(size: Theme.headerMarkSize)
                        .offset(x: -(Theme.headerMarkSize + Theme.headerMarkSpacing))
                }
            HStack {
                AvatarView(avatarKey: user?.avatarKey ?? "",
                           displayName: user?.displayName ?? fallbackName,
                           size: 44)
                    .overlay(Circle().stroke(Color.white, lineWidth: 2))
                    .shadow(color: .black.opacity(0.08), radius: 4, y: 1)
                Spacer()
                HStack(spacing: 22) {
                    Image(systemName: "magnifyingglass")
                    Image(systemName: "qrcode")
                }
                .font(.system(size: 20, weight: .medium))
                .padding(.horizontal, 18)
                .frame(height: 44)
                .background(Capsule().fill(Theme.card))
                .overlay(Capsule().stroke(Color.black.opacity(0.08)))
                .accessibilityHidden(true)
            }
        }
        .foregroundStyle(Theme.primaryText)
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}
