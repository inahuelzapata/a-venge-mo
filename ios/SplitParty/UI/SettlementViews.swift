import SwiftUI

// Pay and its celebration (PoC-11, FR-5.1–5.3): the host's Pay button, the
// pending/paid seat badges, and the paid party screen every member sees (ticks
// one by one, then confetti and a success check).
// Measured values: docs/design/visual-conventions.md ("Pay and settlement").

// MARK: Pay button (host)

/// Under the total card on the host's phone: the Pay button, what it does, and
/// the inline error when Pay failed (nothing was paid then).
struct PayArea: View {
    let party: SplitParty
    let now: Date
    let usersById: [String: UserAccount]
    let isPaying: Bool
    let error: String?
    let onPay: () -> Void

    var body: some View {
        let blocker = party.payBlocker(at: now)
        let collectCents = party.split.map { split in
            party.joineeIds.reduce(0) { $0 + (split.share(of: $1) ?? 0) }
        }
        VStack(spacing: 10) {
            PayButton(blocker: blocker, collectCents: collectCents, payerNames: payerNames,
                      isPaying: isPaying, action: onPay)
            if let error {
                Label(error, systemImage: "exclamationmark.circle.fill")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.errorText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("payError")
            } else if blocker == nil {
                Text("\(NameList.join(payerNames)) pay you their shares. Joining was their OK.")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.secondaryText)
                    .multilineTextAlignment(.center)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, Theme.screenGutter + 8)
        .sensoryFeedback(.error, trigger: error) { _, new in new != nil }
    }

    private var payerNames: [String] {
        party.joineeIds.map { usersById[$0]?.firstName ?? $0 }
    }
}

/// Brand-blue capsule reading "Pay $78.66" when the party can be settled;
/// otherwise grey, disabled, and reading why ("Add the total", "Waiting for
/// friends to join", "Party expired").
struct PayButton: View {
    let blocker: PayBlocker?
    let collectCents: Int?
    let payerNames: [String]
    let isPaying: Bool
    let action: () -> Void

    var body: some View {
        let isEnabled = blocker == nil && !isPaying
        Button(action: action) {
            HStack(spacing: 10) {
                if isPaying {
                    ProgressView().tint(.white)
                }
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .monospacedDigit()
            }
            .foregroundStyle(blocker == nil ? Color.white : Theme.secondaryText)
            .frame(maxWidth: .infinity, minHeight: Theme.payActionHeight)
            .background(Capsule().fill(blocker == nil ? Theme.brandBlue : Theme.actionBackground))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(accessibilityTitle)
        .accessibilityHint(blocker == nil && !isPaying ? "Settles everyone's share at once" : "")
        .accessibilityIdentifier("payButton")
    }

    private var title: String {
        if isPaying { return "Paying…" }
        if let blocker { return blocker.label }
        return collectCents.map { "Pay \(Money.format(cents: $0))" } ?? "Pay"
    }

    /// "Pay, collect 78 dollars and 66 cents from Maxi and Christophe" or
    /// "Pay, unavailable: Add the total".
    private var accessibilityTitle: String {
        if isPaying { return "Paying" }
        if let blocker { return "Pay, unavailable: \(blocker.label)" }
        guard let collectCents else { return "Pay" }
        return "Pay, collect \(Money.spoken(cents: collectCents)) from \(NameList.join(payerNames))"
    }
}

// MARK: Seat badges

/// Top-right of a member's avatar: a grey clock while Pay runs, a blue tick once paid.
struct SeatPayBadge: View {
    let state: SeatPayState

    var body: some View {
        Image(systemName: state == .paid ? "checkmark" : "clock")
            .font(.system(size: 11, weight: .heavy))
            .foregroundStyle(state == .paid ? Color.white : Theme.secondaryText)
            .frame(width: Theme.seatBadgeSize, height: Theme.seatBadgeSize)
            .background(Circle().fill(state == .paid ? Theme.brandBlue : Theme.actionBackground))
            .overlay(Circle().stroke(Color.white, lineWidth: 2))
            .accessibilityHidden(true)
    }
}

// MARK: Paid party

/// The paid party on every member's phone. The first time this phone sees it
/// (just paid, or the first look after reconnecting or relaunching) the friends'
/// seats tick to paid one by one, then confetti and the success check; after
/// that, or with Reduce Motion, the final state shows at once (haptics stay).
struct PaidPartyContent: View {
    let party: SplitParty
    let userId: String
    let usersById: [String: UserAccount]
    let merchantName: String
    let celebrations: CelebrationLedger
    let onBackToHome: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tickedCount = 0
    @State private var showsSuccess = false
    @State private var showsConfetti = false
    /// True when this phone had already celebrated this party: no haptics again.
    @State private var isReplay = false

    /// Time between two seats ticking, and before the first.
    private static let tickInterval: Duration = .milliseconds(280)
    private static let firstTickDelay: Duration = .milliseconds(100)
    private static let successDelay: Duration = .milliseconds(150)
    private static let confettiDuration: Duration = .seconds(2.6)

    var body: some View {
        ZStack(alignment: .top) {
            ScrollView {
                VStack(spacing: Theme.partySectionSpacing) {
                    PartyCard {
                        MemberRow(memberIds: party.memberIds, hostId: party.hostId, usersById: usersById,
                                  onTapMember: nil, payStates: seatStates, title: "Everyone's paid")
                    }
                    if showsSuccess {
                        PartyCard {
                            SettlementSuccess(isHost: userId == party.hostId, amountCents: amountCents,
                                              counterpartNames: counterpartNames, merchantName: merchantName)
                        }
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                        Button("Back to Home", action: onBackToHome)
                            .buttonStyle(PillButtonStyle())
                            .padding(.horizontal, Theme.screenGutter + 8)
                            .accessibilityIdentifier("backToHomeButton")
                    }
                }
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .contentMargins(.bottom, Theme.floatingBarClearance, for: .scrollContent)

            if showsConfetti {
                ConfettiView()
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .sensoryFeedback(trigger: tickedCount) { old, new in
            new > old && !isReplay ? .impact(weight: .light) : nil
        }
        .sensoryFeedback(trigger: showsSuccess) { _, new in new && !isReplay ? .success : nil }
        .task(id: party.id) { await play() }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("paidParty")
    }

    private var joineeIds: [String] { party.joineeIds }

    /// Friends tick in join order; the host never gets a badge (they collect).
    private var seatStates: [String: SeatPayState] {
        var states: [String: SeatPayState] = [:]
        for (index, memberId) in joineeIds.enumerated() {
            states[memberId] = index < tickedCount ? .paid : .pending
        }
        return states
    }

    /// The host's success shows what was collected, a member's what they paid.
    private var amountCents: Int {
        guard let settlement = PartySettlement(party: party, merchantName: merchantName) else { return 0 }
        if userId == party.hostId { return settlement.collectedCents }
        return settlement.payment(of: userId)?.amountCents ?? 0
    }

    private var counterpartNames: [String] {
        let ids = userId == party.hostId ? joineeIds : [party.hostId]
        return ids.map { usersById[$0]?.firstName ?? $0 }
    }

    private func play() async {
        let alreadyCelebrated = celebrations.hasCelebrated(party.id)
        celebrations.record(party.id)
        if alreadyCelebrated || reduceMotion {
            isReplay = alreadyCelebrated
            tickedCount = joineeIds.count
            showsSuccess = true
            if !alreadyCelebrated { announceResult() }
            return
        }
        try? await Task.sleep(for: Self.firstTickDelay)
        for index in joineeIds.indices {
            if index > 0 { try? await Task.sleep(for: Self.tickInterval) }
            guard !Task.isCancelled else { return }
            withAnimation(.spring(response: 0.3, dampingFraction: 0.55)) { tickedCount = index + 1 }
        }
        try? await Task.sleep(for: Self.successDelay)
        guard !Task.isCancelled else { return }
        withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) {
            showsSuccess = true
            showsConfetti = true
        }
        announceResult()
        try? await Task.sleep(for: Self.confettiDuration)
        withAnimation(.easeOut(duration: 0.4)) { showsConfetti = false }
    }

    /// One announcement, so VoiceOver does not cut its own sentences off:
    /// "Maxi, paid. Christophe, paid. You collected 78 dollars and 66 cents."
    private func announceResult() {
        let ticks = joineeIds.map { "\(usersById[$0]?.firstName ?? $0), paid." }.joined(separator: " ")
        UIAccessibility.post(notification: .announcement,
                             argument: "\(ticks) \(SettlementSuccess.spokenResult(isHost: userId == party.hostId, amountCents: amountCents))")
    }
}

/// The success check with the amount: "You collected $78.66" (host) or
/// "You paid $39.33" (member).
struct SettlementSuccess: View {
    let isHost: Bool
    let amountCents: Int
    let counterpartNames: [String]
    let merchantName: String

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var checkScale: CGFloat = 0.4

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: "checkmark")
                .font(.system(size: 34, weight: .heavy))
                .foregroundStyle(.white)
                .frame(width: Theme.successCheckSize, height: Theme.successCheckSize)
                .background(Circle().fill(Theme.brandBlue))
                .scaleEffect(reduceMotion ? 1 : checkScale)
                .padding(.bottom, 8)
                .onAppear {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.5)) { checkScale = 1 }
                }
            Text(isHost ? "You collected" : "You paid")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Theme.secondaryText)
            Text(Money.format(cents: amountCents))
                .font(.system(size: Theme.successAmountFontSize, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Theme.primaryText)
                .accessibilityIdentifier("successAmount")
            Text(detail)
                .font(.system(size: 15))
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Paid. \(Self.spokenResult(isHost: isHost, amountCents: amountCents)) \(detail)")
        .accessibilityIdentifier("settlementSuccess")
    }

    /// "from Maxi and Christophe · Tony's Split Party" / "to Annie · Tony's Split Party".
    private var detail: String {
        "\(isHost ? "from" : "to") \(NameList.join(counterpartNames)) · \(PartySettlement.memo(merchantName: merchantName))"
    }

    static func spokenResult(isHost: Bool, amountCents: Int) -> String {
        "You \(isHost ? "collected" : "paid") \(Money.spoken(cents: amountCents))."
    }
}

// MARK: Confetti

/// A one-shot burst from the top of the screen: pieces fan out, fall with
/// gravity, spin and fade. Drawn in one Canvas; never shown with Reduce Motion.
struct ConfettiView: View {
    private struct Piece {
        let startX: CGFloat
        let horizontalSpeed: CGFloat
        let verticalSpeed: CGFloat
        let spin: Double
        let size: CGSize
        let color: Color
    }

    private static let pieceCount = 90
    private static let gravity: CGFloat = 900
    private static let lifetime: TimeInterval = 2.4
    private static let colors: [Color] = [Theme.brandBlue, Color(hex: 0xFFC83D), Color(hex: 0xFF5C8A),
                                          Color(hex: 0x2BC48A), Color(hex: 0x6CC4FF)]

    @State private var start = Date()
    @State private var pieces: [Piece] = Self.makePieces()

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let elapsed = timeline.date.timeIntervalSince(start)
                let opacity = max(0, 1 - max(0, elapsed - Self.lifetime * 0.6) / (Self.lifetime * 0.4))
                for piece in pieces {
                    let time = CGFloat(elapsed)
                    let x = piece.startX * size.width + piece.horizontalSpeed * time
                    let y = -20 + piece.verticalSpeed * time + Self.gravity * time * time / 2
                    var pieceContext = context
                    pieceContext.opacity = opacity
                    pieceContext.translateBy(x: x, y: y)
                    pieceContext.rotate(by: .degrees(piece.spin * elapsed))
                    let rect = CGRect(origin: CGPoint(x: -piece.size.width / 2, y: -piece.size.height / 2),
                                      size: piece.size)
                    pieceContext.fill(Path(roundedRect: rect, cornerRadius: 1.5), with: .color(piece.color))
                }
            }
        }
        .accessibilityIdentifier("confetti")
    }

    private static func makePieces() -> [Piece] {
        (0..<pieceCount).map { index in
            Piece(startX: .random(in: 0.1...0.9),
                  horizontalSpeed: .random(in: -160...160),
                  verticalSpeed: .random(in: -420 ... -120),
                  spin: .random(in: -540...540),
                  size: CGSize(width: .random(in: 6...10), height: .random(in: 10...16)),
                  color: colors[index % colors.count])
        }
    }
}

// MARK: Helpers

enum NameList {
    /// ["Maxi"] → "Maxi"; ["Maxi", "Christophe"] → "Maxi and Christophe";
    /// three or more → "A, B and C".
    static func join(_ names: [String]) -> String {
        guard let last = names.last else { return "" }
        guard names.count > 1 else { return last }
        return names.dropLast().joined(separator: ", ") + " and " + last
    }
}

/// Which paid parties this phone has already celebrated, so the ticks and
/// confetti play once per party per phone: on the phone that was looking when
/// Pay landed, or on the first look after reconnecting or relaunching.
/// Kept in UserDefaults (a handful of ids).
@MainActor
final class CelebrationLedger {
    private static let key = "celebratedPartyIds"
    /// Old ids are dropped past this; parties are short-lived demo objects.
    private static let maxRemembered = 30

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func hasCelebrated(_ partyId: String) -> Bool {
        ids.contains(partyId)
    }

    func record(_ partyId: String) {
        guard !hasCelebrated(partyId) else { return }
        defaults.set(Array((ids + [partyId]).suffix(Self.maxRemembered)), forKey: Self.key)
    }

    func clear() {
        defaults.removeObject(forKey: Self.key)
    }

    private var ids: [String] {
        defaults.stringArray(forKey: Self.key) ?? []
    }
}
