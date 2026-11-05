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
    private let initialHomePath: [HomeDestination]

    /// `initialTab` and `initialHomePath` let snapshot tests open on Me,
    /// Pay/Request or a screen pushed from Home; the app starts on Home.
    init(initialTab: AppTab = .home, initialHomePath: [HomeDestination] = []) {
        _selectedTab = State(initialValue: initialTab)
        self.initialHomePath = initialHomePath
    }

    var body: some View {
        ZStack {
            switch selectedTab {
            case .home: HomeNavigationStack(initialPath: initialHomePath)
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

/// Screens pushed on top of Home.
enum HomeDestination: Hashable {
    /// The party screen, for host and members alike.
    case party(id: String)
    /// Picking the host's code before joining their party.
    case confirmJoin(partyId: String)
}

/// Home with standard push navigation. Home keeps no navigation bar of its own;
/// pushed screens get the system bar and Back. Home stays alive underneath, so
/// Back returns to the same scroll position.
private struct HomeNavigationStack: View {
    @Environment(LiveDataStore.self) private var liveData
    @State private var path: [HomeDestination]

    init(initialPath: [HomeDestination]) {
        _path = State(initialValue: initialPath)
    }

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(onStartParty: startParty,
                     onOpenParty: { open(.party(id: $0)) },
                     onJoin: { open(.confirmJoin(partyId: $0)) })
                .background(Theme.background.ignoresSafeArea())
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: HomeDestination.self) { destination in
                    switch destination {
                    case let .party(id):
                        PartyView(partyId: id, onBackToHome: { path = [] })
                    case let .confirmJoin(partyId):
                        JoinConfirmationView(partyId: partyId,
                                             onJoined: { path = [.party(id: partyId)] },
                                             onBackToHome: { path = [] })
                    }
                }
        }
        // Relaunch (or sign-in) while in an open party: straight back to it.
        .onChange(of: liveData.parties.partyToOpenOnLaunch, initial: true) { _, partyId in
            guard let partyId else { return }
            liveData.parties.partyToOpenOnLaunch = nil
            path = [.party(id: partyId)]
        }
    }

    /// "Start a party" on the merchant card: opens the new party, or the one the
    /// user is already in. On failure Home shows the inline error and stays put.
    private func startParty() {
        Task {
            if let partyId = await liveData.parties.startParty() {
                open(.party(id: partyId))
            }
        }
    }

    private func open(_ destination: HomeDestination) {
        guard path.last != destination else { return }
        path = [destination]
    }
}

/// Floating capsule with Home and Me as regular tabs and, between them, the
/// Pay/Request action as a round brand-blue button carrying the white
/// Aven-mo mark (Venmo-style). Sizes: Theme, docs/design/visual-conventions.md "Brand mark".
private struct BottomBar: View {
    @Binding var selectedTab: AppTab

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

            // Seated in the bar, centred on it: not lifted and no shadow of its own,
            // so it reads as part of the bar rather than floating over it.
            payRequestButton
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
