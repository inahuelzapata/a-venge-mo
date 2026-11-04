import Observation
import SwiftUI

/// Live state of one party for its screen: the party document (whatever its
/// status) and whether it is confirmed by the server right now.
@MainActor
@Observable
final class PartyRoomModel {
    let partyId: String
    private(set) var party: SplitParty?
    private(set) var hasLoaded = false
    /// Firestore is serving the cache, i.e. the server is out of reach.
    private(set) var isFromCache = false

    @ObservationIgnored private let repository: any PaymentDataSource
    @ObservationIgnored private var subscription: LiveSubscription?
    @ObservationIgnored private var isStopped = false
    private static let relistenDelay: Duration = .seconds(2)

    init(partyId: String, repository: any PaymentDataSource) {
        self.partyId = partyId
        self.repository = repository
        listen()
    }

    func stop() {
        isStopped = true
        subscription?.cancel()
    }

    private func listen() {
        subscription = repository.listenToParty(id: partyId) { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(document):
                Motion.animate(.spring(response: 0.45, dampingFraction: 0.7)) {
                    self.party = document.item
                }
                self.isFromCache = document.isFromCache
                self.hasLoaded = true
            case .failure:
                self.isFromCache = true
                Task { [weak self] in
                    try? await Task.sleep(for: Self.relistenDelay)
                    guard let self, !self.isStopped else { return }
                    self.listen()
                }
            }
        }
    }
}

/// The Split Party screen, the same for host and members (PoC-8/9): the code to
/// show friends, the countdown to expiry, who is in, and the space the total
/// slice fills in. No Pay, leave or remove actions in this slice.
/// Measured values: docs/design/visual-conventions.md ("Party screen").
struct PartyView: View {
    let partyId: String
    let onBackToHome: () -> Void

    @Environment(LiveDataStore.self) private var liveData
    @State private var room: PartyRoomModel?

    var body: some View {
        let hub = liveData.parties
        Group {
            if let room {
                content(room: room, hub: hub)
            } else {
                Color.clear
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Split Party at \(hub.merchantName)")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if room == nil { room = PartyRoomModel(partyId: partyId, repository: hub.repository) }
        }
        .onDisappear { room?.stop(); room = nil }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("partyScreen")
        // Lets UI tests tell parties apart; VoiceOver users never land on the container.
        .accessibilityValue(partyId)
    }

    @ViewBuilder
    private func content(room: PartyRoomModel, hub: PartyHub) -> some View {
        if let party = room.party {
            if party.isExpired(at: hub.now) {
                PartyClosedView(title: "This party has expired", message: "Nothing was charged.",
                                systemImage: "clock.badge.xmark", onBackToHome: onBackToHome)
            } else if party.status == .paid {
                PartyClosedView(title: "This party is settled", message: "Everyone's share has been paid.",
                                systemImage: "checkmark.circle", onBackToHome: onBackToHome)
            } else {
                OpenPartyContent(party: party, now: hub.now, usersById: liveData.usersById,
                                 isReconnecting: room.isFromCache || !hub.connection.isNetworkAvailable)
            }
        } else if room.hasLoaded {
            PartyClosedView(title: "This party isn't available", message: "It may have been cleared.",
                            systemImage: "questionmark.circle", onBackToHome: onBackToHome)
        } else {
            ProgressView()
        }
    }
}

// MARK: Open party

private struct OpenPartyContent: View {
    let party: SplitParty
    let now: Date
    let usersById: [String: UserAccount]
    let isReconnecting: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.partySectionSpacing) {
                if isReconnecting {
                    ReconnectingIndicator()
                        .transition(.opacity)
                }
                PartyCard {
                    VStack(spacing: 14) {
                        Text("Show friends this code")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(Theme.secondaryText)
                            .accessibilityHidden(true)
                        JoinCodeTiles(code: party.joinCode, animatesIn: true)
                        CountdownLabel(secondsLeft: party.secondsLeft(at: now))
                    }
                }
                PartyCard {
                    MemberRow(memberIds: party.memberIds, hostId: party.hostId, usersById: usersById)
                }
                PartyCard {
                    TotalPlaceholder()
                }
            }
            .padding(.top, 8)
            .padding(.bottom, 24)
            .animation(.easeInOut(duration: 0.25), value: isReconnecting)
        }
        .scrollIndicators(.hidden)
        // A haptic on the phones already in the party when someone new arrives.
        // The first delivery (0 → n) is the party loading, not news.
        .sensoryFeedback(trigger: party.memberIds.count) { old, new in
            new > old && old > 0 ? .success : nil
        }
    }
}

/// White rounded section card, like the feed rows.
private struct PartyCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity)
            .padding(Theme.cardPadding)
            .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous).fill(Theme.card))
            .padding(.horizontal, Theme.screenGutter)
    }
}

private struct ReconnectingIndicator: View {
    var body: some View {
        HStack(spacing: 8) {
            ProgressView().controlSize(.small)
            Text("Reconnecting…")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.secondaryText)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Capsule().fill(Theme.card))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("reconnectingIndicator")
    }
}

// MARK: Code

/// The three emoji tiles. They pop in one by one, then bob gently out of step.
/// VoiceOver reads "Join code: pizza, beer, party popper".
struct JoinCodeTiles: View {
    let code: JoinCode
    var animatesIn = false
    var tileSize: CGFloat = Theme.codeTileSize
    var emojiSize: CGFloat = Theme.codeEmojiFontSize

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visibleCount = 0
    @State private var isBobbing = false

    private static let popInterval: Double = 0.14
    private static let bobDistance: CGFloat = 3

    var body: some View {
        HStack(spacing: Theme.codeTileSpacing) {
            ForEach(Array(code.emojis.enumerated()), id: \.offset) { index, emoji in
                Text(emoji)
                    .font(.system(size: emojiSize))
                    .frame(width: tileSize, height: tileSize)
                    .background(RoundedRectangle(cornerRadius: Theme.codeTileCornerRadius, style: .continuous)
                        .fill(Theme.actionBackground))
                    .scaleEffect(index < shownCount ? 1 : 0.2)
                    .opacity(index < shownCount ? 1 : 0)
                    .offset(y: isBobbing ? (index.isMultiple(of: 2) ? -Self.bobDistance : Self.bobDistance) : 0)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Join code: \(code.spokenNames)")
        .accessibilityIdentifier("joinCode")
        .task {
            guard animatesIn, !reduceMotion else { return }
            for index in 1...code.emojis.count {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) { visibleCount = index }
                try? await Task.sleep(for: .seconds(Self.popInterval))
            }
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { isBobbing = true }
        }
    }

    /// Without the entrance (or with Reduce Motion) every tile is simply there.
    private var shownCount: Int {
        animatesIn && !reduceMotion ? visibleCount : code.emojis.count
    }
}

private struct CountdownLabel: View {
    let secondsLeft: Int

    var body: some View {
        Label("Expires in \(Countdown.text(seconds: secondsLeft))", systemImage: "timer")
            .font(.system(size: 17, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(secondsLeft <= 30 ? Theme.errorText : Theme.primaryText)
            .contentTransition(.numericText(countsDown: true))
            .accessibilityIdentifier("partyCountdown")
    }
}

// MARK: Members

/// Every member in join order (host first) with avatar, first name and a Host
/// badge, then empty seats that pulse while waiting for friends.
private struct MemberRow: View {
    let memberIds: [String]
    let hostId: String
    let usersById: [String: UserAccount]

    /// The demo party is the host plus two friends; a bigger party just grows.
    private static let minimumSeats = 3

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Who's in")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(alignment: .top, spacing: Theme.memberSeatSpacing) {
                ForEach(memberIds, id: \.self) { memberId in
                    MemberSeat(user: usersById[memberId], userId: memberId, isHost: memberId == hostId)
                        .transition(.scale(scale: 0.3).combined(with: .opacity))
                }
                ForEach(0..<max(0, Self.minimumSeats - memberIds.count), id: \.self) { _ in
                    EmptySeat()
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("memberRow")
    }
}

private struct MemberSeat: View {
    let user: UserAccount?
    let userId: String
    let isHost: Bool

    var body: some View {
        VStack(spacing: 6) {
            AvatarView(avatarKey: user?.avatarKey ?? "", displayName: user?.displayName ?? userId,
                       size: Theme.memberAvatarSize)
                .overlay(alignment: .bottom) {
                    if isHost {
                        Text("Host")
                            .font(.system(size: Theme.hostBadgeFontSize, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.black))
                            .overlay(Capsule().stroke(Color.white, lineWidth: 1.5))
                            .offset(y: 7)
                    }
                }
            Text(user?.firstName ?? userId)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.primaryText)
                .lineLimit(1)
                // Long first names shrink a little rather than being cut off.
                .minimumScaleFactor(0.8)
                .padding(.top, isHost ? 4 : 0)
        }
        .frame(width: Theme.memberSeatWidth)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(isHost ? "\(user?.firstName ?? userId), host" : (user?.firstName ?? userId))
        .accessibilityIdentifier("memberSeat")
    }
}

private struct EmptySeat: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isPulsing = false

    var body: some View {
        VStack(spacing: 6) {
            Circle()
                .strokeBorder(Theme.secondaryText.opacity(0.5), style: StrokeStyle(lineWidth: 2, dash: [5, 4]))
                .background(Circle().fill(Theme.emptySeat.opacity(isPulsing ? 0.35 : 1)))
                .frame(width: Theme.memberAvatarSize, height: Theme.memberAvatarSize)
                .scaleEffect(isPulsing ? 0.94 : 1)
            Text("Waiting")
                .font(.system(size: 14))
                .foregroundStyle(Theme.secondaryText)
        }
        .frame(width: Theme.memberSeatWidth)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { isPulsing = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Empty seat")
        .accessibilityIdentifier("emptySeat")
    }
}

// MARK: Total

/// Reserved for the total / tip / share slice (PoC-10, FR-3/FR-4).
private struct TotalPlaceholder: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "hourglass")
                .font(.system(size: 24))
                .foregroundStyle(Theme.secondaryText)
                .accessibilityHidden(true)
            Text("Waiting for the total")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
            Text("The host enters the bill and tip next.")
                .font(.system(size: 14))
                .foregroundStyle(Theme.secondaryText)
        }
        .frame(maxWidth: .infinity, minHeight: 120)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("totalPlaceholder")
    }
}

// MARK: Closed

/// Expired, settled or missing: say so and offer the way back to Home.
struct PartyClosedView: View {
    let title: String
    let message: String
    let systemImage: String
    let onBackToHome: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 52))
                .foregroundStyle(Theme.secondaryText)
                .accessibilityHidden(true)
            Text(title)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Theme.primaryText)
                .multilineTextAlignment(.center)
            Text(message)
                .font(.system(size: 17))
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            Button("Back to Home", action: onBackToHome)
                .buttonStyle(PillButtonStyle())
                .padding(.top, 12)
                .accessibilityIdentifier("backToHomeButton")
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
