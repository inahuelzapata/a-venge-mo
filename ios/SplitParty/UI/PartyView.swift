import Observation
import SwiftUI

/// Live state of one party for its screen: the party document (whatever its
/// status) and whether it is confirmed by the server right now.
@MainActor
@Observable
final class PartyRoomModel {
    let partyId: String
    /// The signed-in user looking at this party.
    let userId: String
    private(set) var party: SplitParty?
    private(set) var hasLoaded = false
    /// Firestore is serving the cache, i.e. the server is out of reach.
    private(set) var isFromCache = false
    /// True once this user was seen in the party and then dropped by someone else
    /// (the host removed them). Leaving on purpose does not set it.
    private(set) var wasRemoved = false
    /// A write is in flight; buttons that would write again are disabled.
    private(set) var isSaving = false
    /// The host's Pay is in flight: the joinees' seats show "pending".
    private(set) var isPaying = false
    /// The party was there and then deleted (Demo reset): the screen goes Home.
    private(set) var wasDeleted = false

    @ObservationIgnored private let repository: any PaymentDataSource
    @ObservationIgnored private let connection: ConnectionMonitor
    @ObservationIgnored private var subscription: LiveSubscription?
    @ObservationIgnored private var isStopped = false
    @ObservationIgnored private var hasBeenMember = false
    @ObservationIgnored private var isLeaving = false
    private static let relistenDelay: Duration = .seconds(2)

    init(partyId: String, userId: String, repository: any PaymentDataSource, connection: ConnectionMonitor) {
        self.partyId = partyId
        self.userId = userId
        self.repository = repository
        self.connection = connection
        listen()
    }

    var isHost: Bool {
        party?.hostId == userId
    }

    // MARK: Actions

    /// Each returns nil on success or the inline error to show; on failure the
    /// party is unchanged.

    func confirmBill(cents: Int) async -> String? {
        await write { try await $0.setBill(partyId: self.partyId, billCents: cents) }
    }

    func chooseTip(_ tip: TipChoice) async -> String? {
        await write { try await $0.setTip(partyId: self.partyId, tip: tip) }
    }

    /// Host only; the host can never remove themselves.
    func removeMember(_ memberId: String) async -> String? {
        guard memberId != party?.hostId else { return nil }
        return await write { try await $0.removeMember(partyId: self.partyId, memberId: memberId) }
    }

    /// Non-host members only.
    func leave() async -> String? {
        guard !isHost else { return nil }
        isLeaving = true
        let error = await write { try await $0.removeMember(partyId: self.partyId, memberId: self.userId) }
        if error != nil { isLeaving = false }
        return error
    }

    /// Host only. Nil on success (the listener then turns the screen to paid),
    /// or the inline error; on failure nothing was paid. A second call while one
    /// is in flight does nothing, and a retry after a failure is always safe.
    func pay() async -> String? {
        guard isHost, !isPaying else { return nil }
        guard connection.isNetworkAvailable else { return PartyActionError.offline.payMessage }
        isPaying = true
        defer { isPaying = false }
        do {
            try await repository.settleParty(id: partyId)
            return nil
        } catch {
            return (error as? PartyActionError ?? .unreachable).payMessage
        }
    }

    private func write(_ operation: (any PaymentDataSource) async throws -> Void) async -> String? {
        guard connection.isNetworkAvailable else { return PartyActionError.offline.changeMessage }
        isSaving = true
        defer { isSaving = false }
        do {
            try await operation(repository)
            return nil
        } catch {
            return (error as? PartyActionError ?? .unreachable).changeMessage
        }
    }

    func stop() {
        isStopped = true
        subscription?.cancel()
    }

    private func noticeRemoval(in party: SplitParty?) {
        guard let party else { return }
        if party.isMember(userId) {
            hasBeenMember = true
        } else if hasBeenMember && !isLeaving && party.isActive(at: Date()) {
            wasRemoved = true
        }
    }

    private func listen() {
        subscription = repository.listenToParty(id: partyId) { [weak self] result in
            guard let self else { return }
            switch result {
            case let .success(document):
                // One state change per delivery: total, headcount and share all
                // come from this value, so they always change in the same frame.
                // Turning paid swaps the screen at once: the paid screen plays its
                // own ticks from the first frame, and a crossfade would hide them.
                if document.item?.status == .paid && self.party?.status != .paid {
                    self.party = document.item
                } else {
                    Motion.animate(.spring(response: 0.45, dampingFraction: 0.7)) {
                        self.party = document.item
                    }
                }
                self.noticeRemoval(in: document.item)
                if document.item == nil, self.hasLoaded, !document.isFromCache { self.wasDeleted = true }
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
            hub.rememberLastParty(partyId)
            if room == nil {
                room = PartyRoomModel(partyId: partyId, userId: hub.userId, repository: hub.repository,
                                      connection: hub.connection)
            }
        }
        .onDisappear { room?.stop(); room = nil }
        // Demo reset deleted the party: every phone on it goes back Home.
        .onChange(of: room?.wasDeleted == true) { _, wasDeleted in
            guard wasDeleted else { return }
            hub.forgetLastParty()
            onBackToHome()
        }
        // Removed by the host: back to Home, where the Join banner offers a rejoin.
        .onChange(of: room?.wasRemoved == true) { _, wasRemoved in
            guard wasRemoved, let party = room?.party else { return }
            let hostName = liveData.usersById[party.hostId]?.firstName ?? party.hostId
            hub.showHomeNotice("You were removed from \(hostName)'s party")
            hub.forgetLastParty()
            onBackToHome()
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("partyScreen")
        // Lets UI tests tell parties apart; VoiceOver users never land on the container.
        .accessibilityValue(partyId)
    }

    @ViewBuilder
    private func content(room: PartyRoomModel, hub: PartyHub) -> some View {
        if let party = room.party {
            if party.isExpired(at: hub.now) {
                // The last total, tip and share stay on screen, frozen (no controls).
                PartyClosedView(title: "This party has expired", message: "Nothing was charged.",
                                systemImage: "clock.badge.xmark", onBackToHome: leaveScreen,
                                frozenSplit: party.split, frozenTip: party.tip, userId: room.userId,
                                showsDisabledPay: room.isHost)
            } else if party.status == .paid && party.isMember(room.userId) {
                PaidPartyContent(party: party, userId: room.userId, usersById: liveData.usersById,
                                 merchantName: hub.merchantName, celebrations: hub.celebrations,
                                 onBackToHome: leaveScreen)
            } else if party.status == .paid {
                PartyClosedView(title: "This party is settled", message: "Everyone's share has been paid.",
                                systemImage: "checkmark.circle", onBackToHome: leaveScreen)
            } else {
                OpenPartyContent(party: party, room: room, now: hub.now, usersById: liveData.usersById,
                                 isReconnecting: room.isFromCache || !hub.connection.isNetworkAvailable,
                                 onLeft: onBackToHome)
            }
        } else if room.hasLoaded {
            PartyClosedView(title: "This party isn't available", message: "It may have been cleared.",
                            systemImage: "questionmark.circle", onBackToHome: leaveScreen)
        } else {
            ProgressView()
        }
    }

    /// "Back to Home" from a finished party: also stops a relaunch reopening it.
    private func leaveScreen() {
        liveData.parties.forgetLastParty()
        onBackToHome()
    }
}

// MARK: Open party

private struct OpenPartyContent: View {
    let party: SplitParty
    let room: PartyRoomModel
    let now: Date
    let usersById: [String: UserAccount]
    let isReconnecting: Bool
    let onLeft: () -> Void

    @State private var memberToRemove: String?
    @State private var isConfirmingLeave = false
    @State private var memberError: String?
    @State private var payError: String?

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
                    VStack(alignment: .leading, spacing: 10) {
                        MemberRow(memberIds: party.memberIds, hostId: party.hostId, usersById: usersById,
                                  onTapMember: room.isHost && !room.isPaying ? { memberToRemove = $0 } : nil,
                                  payStates: room.isPaying ? pendingStates : [:])
                        if let memberError {
                            Label(memberError, systemImage: "exclamationmark.circle.fill")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(Theme.errorText)
                                .accessibilityIdentifier("memberError")
                        }
                    }
                }
                PartyCard {
                    PartyTotalSection(party: party, room: room, usersById: usersById)
                }
                if room.isHost {
                    PayArea(party: party, now: now, usersById: usersById, isPaying: room.isPaying,
                            error: payError, onPay: pay)
                }
                if !room.isHost {
                    Button("Leave party", role: .destructive) { isConfirmingLeave = true }
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.errorText)
                        .frame(minHeight: 44)
                        .disabled(room.isSaving)
                        .accessibilityIdentifier("leavePartyButton")
                }
            }
            .padding(.top, 8)
            .padding(.bottom, 24)
            .animation(.easeInOut(duration: 0.25), value: isReconnecting)
        }
        .scrollIndicators(.hidden)
        .confirmationDialog(removeTitle, isPresented: isConfirmingRemove, titleVisibility: .visible) {
            if let memberId = memberToRemove {
                Button("Remove \(firstName(memberId))", role: .destructive) { remove(memberId) }
                    .accessibilityIdentifier("confirmRemoveMember")
            }
        } message: {
            Text("Everyone's share is recalculated. They can rejoin with the code while the party is open.")
        }
        .confirmationDialog("Leave \(firstName(party.hostId))'s party?", isPresented: $isConfirmingLeave,
                            titleVisibility: .visible) {
            Button("Leave party", role: .destructive) { leave() }
                .accessibilityIdentifier("confirmLeaveParty")
        } message: {
            Text("Your share goes back to the others. You can rejoin with the code while the party is open.")
        }
        // The floating tab bar is drawn over pushed screens without insetting them;
        // without this the tip chips and total buttons could end up under it.
        .contentMargins(.bottom, Theme.floatingBarClearance, for: .scrollContent)
        // A haptic on the phones already in the party when someone new arrives.
        // The first delivery (0 → n) is the party loading, not news.
        .sensoryFeedback(trigger: party.memberIds.count) { old, new in
            new > old && old > 0 ? .success : nil
        }
    }
}

private extension OpenPartyContent {
    var isConfirmingRemove: Binding<Bool> {
        Binding(get: { memberToRemove != nil }, set: { if !$0 { memberToRemove = nil } })
    }

    var removeTitle: String {
        "Remove \(firstName(memberToRemove ?? "")) from the party?"
    }

    func firstName(_ userId: String) -> String {
        usersById[userId]?.firstName ?? userId
    }

    func remove(_ memberId: String) {
        memberError = nil
        Task { memberError = await room.removeMember(memberId) }
    }

    /// While Pay is in flight every friend's seat reads "pending".
    var pendingStates: [String: SeatPayState] {
        Dictionary(uniqueKeysWithValues: party.joineeIds.map { ($0, .pending) })
    }

    func pay() {
        payError = nil
        Task { payError = await room.pay() }
    }

    func leave() {
        memberError = nil
        Task {
            if let failure = await room.leave() {
                memberError = failure
            } else {
                onLeft()
            }
        }
    }
}

/// White rounded section card, like the feed rows.
struct PartyCard<Content: View>: View {
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

/// The two emoji tiles. They pop in one by one, then bob gently out of step.
/// VoiceOver reads "Join code: pizza, beer".
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
struct MemberRow: View {
    let memberIds: [String]
    let hostId: String
    let usersById: [String: UserAccount]
    /// Host only: tapping a non-host seat offers to remove that member.
    let onTapMember: ((String) -> Void)?
    /// Pay status badges (pending while Pay runs, paid once settled).
    var payStates: [String: SeatPayState] = [:]
    var title = "Who's in"

    /// The demo party is the host plus two friends; a bigger party just grows.
    private static let minimumSeats = 3

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(alignment: .top, spacing: Theme.memberSeatSpacing) {
                ForEach(memberIds, id: \.self) { memberId in
                    seat(for: memberId)
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

    @ViewBuilder
    private func seat(for memberId: String) -> some View {
        let seat = MemberSeat(user: usersById[memberId], userId: memberId, isHost: memberId == hostId,
                              payState: payStates[memberId])
        if let onTapMember, memberId != hostId {
            Button { onTapMember(memberId) } label: { seat }
                .buttonStyle(.plain)
                .accessibilityHint("Double-tap to remove from the party")
        } else {
            seat
        }
    }
}

/// A seat's Pay status: "pending" while the host's Pay runs, then a tick.
enum SeatPayState: Equatable {
    case pending
    case paid
}

private struct MemberSeat: View {
    let user: UserAccount?
    let userId: String
    let isHost: Bool
    var payState: SeatPayState?

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
                .overlay(alignment: .topTrailing) {
                    if let payState {
                        SeatPayBadge(state: payState)
                            .offset(x: 4, y: -4)
                            .transition(.scale(scale: 0.2).combined(with: .opacity))
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
        .accessibilityLabel(accessibilityText)
        .accessibilityIdentifier("memberSeat")
    }
}

private extension MemberSeat {
    /// "Annie, host", "Maxi, paid", "Christophe, pending".
    var accessibilityText: String {
        let name = user?.firstName ?? userId
        if isHost { return "\(name), host" }
        switch payState {
        case .paid: return "\(name), paid"
        case .pending: return "\(name), pending"
        case nil: return name
        }
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

/// What members see until the host confirms a bill.
struct TotalPlaceholder: View {
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
    /// An expired party that had a bill keeps its last numbers on screen.
    var frozenSplit: PartySplit?
    var frozenTip: TipChoice?
    var userId = ""
    /// The host of an expired party still sees Pay, disabled, saying why.
    var showsDisabledPay = false

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
            if let frozenSplit {
                ShareSummary(split: frozenSplit, tip: frozenTip, userId: userId)
                    .padding(Theme.cardPadding)
                    .background(RoundedRectangle(cornerRadius: Theme.cardCornerRadius, style: .continuous)
                        .fill(Theme.card))
                    .opacity(0.7)
                    .padding(.top, 12)
                    .accessibilityIdentifier("frozenShareSummary")
            }
            if showsDisabledPay {
                PayButton(blocker: .expired, collectCents: nil, payerNames: [], isPaying: false, action: {})
                    .padding(.top, 12)
            }
            Button("Back to Home", action: onBackToHome)
                .buttonStyle(PillButtonStyle())
                .padding(.top, 12)
                .accessibilityIdentifier("backToHomeButton")
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
