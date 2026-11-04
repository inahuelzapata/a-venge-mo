import SwiftUI

/// Where "Start a party" lands until the real Split Party screen exists.
///
/// This is the one place a future party screen replaces: swap the view returned
/// for `HomeDestination.splitParty` (MainTabView.swift). The banner card and the
/// navigation into this screen stay as they are.
struct SplitPartyPlaceholderView: View {
    static let title = "Split Party"

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "person.3.fill")
                .font(.system(size: 52))
                .foregroundStyle(Theme.brandBlue)
                .accessibilityHidden(true)
            Text("In progress")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Theme.primaryText)
            Text("We're still building Split Party. Soon you'll start a party here and split the check with friends.")
                .font(.system(size: 17))
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(Self.title)
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("splitPartyPlaceholderScreen")
    }
}
