import SwiftUI

enum AppTab: CaseIterable {
    case home, payRequest, me

    var title: String {
        switch self {
        case .home: "Home"
        case .payRequest: "Pay/Request"
        case .me: "Me"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house.fill"
        case .payRequest: "dollarsign"
        case .me: "person.fill"
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

/// Floating capsule with Home, Pay/Request and Me. Pay/Request is the round
/// brand-blue button, like the action button in the reference.
private struct BottomBar: View {
    @Binding var selectedTab: AppTab

    private static let payButtonSize: CGFloat = 52

    var body: some View {
        HStack(spacing: 4) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Button { selectedTab = tab } label: { item(for: tab) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tab.title)
                    .accessibilityAddTraits(selectedTab == tab ? .isSelected : [])
            }
        }
        .padding(6)
        .background(Capsule().fill(.regularMaterial))
        .overlay(Capsule().stroke(Color.black.opacity(0.06)))
        .shadow(color: .black.opacity(0.12), radius: 16, y: 4)
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("bottomBar")
    }

    @ViewBuilder
    private func item(for tab: AppTab) -> some View {
        let isSelected = selectedTab == tab
        VStack(spacing: 3) {
            if tab == .payRequest {
                Image(systemName: tab.systemImage)
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundStyle(.white)
                    .frame(width: Self.payButtonSize, height: Self.payButtonSize)
                    .background(Circle().fill(Theme.brandBlue))
            } else {
                Image(systemName: tab.systemImage)
                    .font(.system(size: 22, weight: .semibold))
                    .frame(height: Self.payButtonSize)
            }
            Text(tab.title)
                .font(.system(size: 11, weight: isSelected ? .semibold : .regular))
        }
        .foregroundStyle(isSelected ? Theme.primaryText : Theme.secondaryText)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(Capsule().fill(isSelected && tab != .payRequest ? Color.black.opacity(0.06) : .clear))
        .contentShape(Capsule())
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
