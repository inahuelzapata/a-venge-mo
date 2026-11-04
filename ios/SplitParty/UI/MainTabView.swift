import SwiftUI

enum AppTab: CaseIterable {
    case home, payRequest, me

    /// Also the VoiceOver label of each bar item.
    var title: String {
        switch self {
        case .home: "Home"
        case .payRequest: "Pay or request"
        case .me: "Me"
        }
    }
}

/// The signed-in shell: one screen at a time with the floating bottom bar on top.
/// A custom bar instead of `TabView` because the reference bar is a floating
/// capsule with a round brand-blue action button, which `TabView` can't draw.
struct MainTabView: View {
    @State private var selectedTab: AppTab

    /// `initialTab` lets snapshot tests open on Me or Pay/Request; the app starts on Home.
    init(initialTab: AppTab = .home) {
        _selectedTab = State(initialValue: initialTab)
    }

    var body: some View {
        ZStack {
            switch selectedTab {
            case .home: HomeView()
            case .payRequest: PayRequestPlaceholderView()
            case .me: MeView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar(selectedTab: $selectedTab)
        }
    }
}

/// Floating capsule with Home and Me as regular tabs and, between them, the
/// Pay/Request action as a raised round brand-blue button carrying the white
/// Aven-mo mark (Venmo-style). Sizes: Theme, docs/design/visual-conventions.md "Brand mark".
private struct BottomBar: View {
    @Binding var selectedTab: AppTab

    /// Moves the button up from the bar's centre line so its top edge sits
    /// `payButtonLift` above the bar's top edge.
    private static let payButtonOffset = -(Theme.bottomBarHeight / 2 - Theme.payButtonDiameter / 2 + Theme.payButtonLift)

    var body: some View {
        ZStack {
            HStack(spacing: 4) {
                tabButton(.home, systemImage: "house.fill")
                // The centre third stays empty under the button, so Home and Me
                // keep their equal-thirds positions.
                Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                tabButton(.me, systemImage: "person.fill")
            }
            .padding(6)
            .background(Capsule().fill(.regularMaterial))
            .overlay(Capsule().stroke(Color.black.opacity(0.06)))
            .shadow(color: .black.opacity(0.12), radius: 16, y: 4)

            payRequestButton
                .offset(y: Self.payButtonOffset)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("bottomBar")
    }

    private func tabButton(_ tab: AppTab, systemImage: String) -> some View {
        let isSelected = selectedTab == tab
        return Button { selectedTab = tab } label: {
            VStack(spacing: 3) {
                Image(systemName: systemImage)
                    .font(.system(size: 22, weight: .semibold))
                    .frame(height: Theme.tabIconSlot)
                Text(tab.title)
                    .font(.system(size: 11, weight: isSelected ? .semibold : .regular))
            }
            .foregroundStyle(isSelected ? Theme.primaryText : Theme.secondaryText)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Capsule().fill(isSelected ? Color.black.opacity(0.06) : .clear))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    /// Looks the same whether or not Pay/Request is the open screen.
    private var payRequestButton: some View {
        Button { selectedTab = .payRequest } label: {
            BrandMark(size: Theme.payButtonMarkSize, color: .white)
                .frame(width: Theme.payButtonDiameter, height: Theme.payButtonDiameter)
                .background(Circle().fill(Theme.brandBlue))
                // Elevation 8, matching Android's shadowElevation = 8.dp.
                .shadow(color: .black.opacity(0.24), radius: 8, y: 4)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(AppTab.payRequest.title)
        .accessibilityAddTraits(selectedTab == .payRequest ? .isSelected : [])
    }
}

/// Pay/Request is not functional in this slice.
struct PayRequestPlaceholderView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "dollarsign.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(Theme.brandBlue)
            Text("Pay or request")
                .font(.system(size: 24, weight: .bold))
            Text("Coming soon")
                .foregroundStyle(Theme.secondaryText)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("payRequestScreen")
    }
}
