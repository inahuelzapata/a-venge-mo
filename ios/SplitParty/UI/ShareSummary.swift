import SwiftUI

/// Once a bill exists, every phone (host included) shows its own share large,
/// then the total, the tip and the headcount (FR-4). All four come from one
/// party value, so they change in the same frame; the share and total roll
/// between old and new values. With Reduce Motion they change instantly, and the
/// haptic stays.
/// Measured values: docs/design/visual-conventions.md ("Total area").
struct ShareSummary: View {
    let split: PartySplit
    let tip: TipChoice?
    let userId: String

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let rollAnimation = Animation.spring(response: 0.5, dampingFraction: 0.85)

    var body: some View {
        VStack(spacing: 14) {
            if let share = split.share(of: userId) {
                VStack(spacing: 2) {
                    Text("Your share")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.secondaryText)
                    Text(Money.format(cents: share))
                        .font(.system(size: Theme.shareAmountFontSize, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Theme.primaryText)
                        .contentTransition(.numericText(value: Double(share)))
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Your share")
                .accessibilityValue(Money.spoken(cents: share))
                .accessibilityIdentifier("myShare")
            }
            HStack(spacing: 0) {
                Stat(title: "Total", value: Money.format(cents: split.totalCents),
                     spokenValue: Money.spoken(cents: split.totalCents), rollValue: split.totalCents,
                     identifier: "partyTotal")
                Stat(title: "Tip", value: (tip ?? .zero).label, detail: Money.format(cents: split.tipCents),
                     spokenValue: spokenTip, rollValue: split.tipCents, identifier: "partyTip")
                Stat(title: "People", value: "\(split.headcount)",
                     spokenValue: split.headcount == 1 ? "1 person" : "\(split.headcount) people",
                     rollValue: split.headcount, identifier: "partyHeadcount")
            }
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Theme.actionBackground))
        }
        .frame(maxWidth: .infinity)
        .animation(reduceMotion ? nil : Self.rollAnimation, value: split)
        .sensoryFeedback(.impact(weight: .light), trigger: split.share(of: userId))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shareSummary")
    }

    /// "18 percent, 18 dollars" or "No tip".
    private var spokenTip: String {
        let chosen = tip ?? .zero
        if chosen.isZero { return "No tip" }
        if case let .percent(percent) = chosen {
            return "\(percent) percent, \(Money.spoken(cents: split.tipCents))"
        }
        return Money.spoken(cents: split.tipCents)
    }
}

/// One column of the total / tip / people row.
private struct Stat: View {
    let title: String
    let value: String
    var detail: String?
    let spokenValue: String
    let rollValue: Int
    let identifier: String

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Theme.secondaryText)
            Text(value)
                .font(.system(size: Theme.totalAmountFontSize, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.primaryText)
                .contentTransition(.numericText(value: Double(rollValue)))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            if let detail {
                Text(detail)
                    .font(.system(size: 13))
                    .monospacedDigit()
                    .foregroundStyle(Theme.secondaryText)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(spokenValue)
        .accessibilityIdentifier(identifier)
    }
}

// MARK: Tip chips (host only)

/// 0%, 15%, 18%, 20% and Custom. The selection follows the party document; a
/// tapped chip shows as chosen right away while the write is in flight. A tip
/// no chip writes (25%, from another phone) shows on the Custom chip.
struct TipChipRow: View {
    let party: SplitParty
    let room: PartyRoomModel
    let onCustom: () -> Void

    @State private var pendingChip: TipChip?
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Tip")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
                .accessibilityHidden(true)
            HStack(spacing: Theme.tipChipSpacing) {
                ForEach(TipChip.allCases, id: \.self) { chip in
                    chipButton(chip)
                }
            }
            if let errorMessage {
                Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.errorText)
                    .accessibilityIdentifier("tipError")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Tip")
        .accessibilityIdentifier("tipChips")
        .onChange(of: party.tip) { pendingChip = nil }
    }

    private var selectedChip: TipChip {
        pendingChip ?? TipChip(for: party.tip)
    }

    private func chipButton(_ chip: TipChip) -> some View {
        let isSelected = chip == selectedChip
        let title = chip == .custom && isSelected && !TipChip.isPreset(party.tip) && pendingChip == nil
            ? (party.tip ?? .zero).label : chip.title
        return Button {
            select(chip)
        } label: {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(isSelected ? .white : Theme.primaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, minHeight: Theme.tipChipHeight)
                .background(Capsule().fill(isSelected ? Color.black : Theme.actionBackground))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(room.isSaving)
        .accessibilityLabel(chip.spokenTitle)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("tipChip-\(chip.title)")
        .sensoryFeedback(.selection, trigger: isSelected) { _, new in new }
    }

    private func select(_ chip: TipChip) {
        errorMessage = nil
        guard let tip = chip.tip else {
            onCustom()
            return
        }
        guard tip != party.tip else { return }
        pendingChip = chip
        UIAccessibility.post(notification: .announcement, argument: "\(chip.spokenTitle) tip selected")
        Task {
            if let failure = await room.chooseTip(tip) {
                pendingChip = nil
                errorMessage = failure
            }
        }
    }
}

/// The chips the host can tap. 0% has no percent preset in the schema and is
/// stored as a custom tip of 0 cents (chiron-memory decisions).
enum TipChip: CaseIterable, Hashable {
    case none, fifteen, eighteen, twenty, custom

    init(for tip: TipChoice?) {
        switch tip ?? .zero {
        case .zero: self = .none
        case .percent(15): self = .fifteen
        case .percent(18): self = .eighteen
        case .percent(20): self = .twenty
        default: self = .custom
        }
    }

    static func isPreset(_ tip: TipChoice?) -> Bool {
        TipChip(for: tip) != .custom
    }

    /// Nil for Custom, which asks for an amount first.
    var tip: TipChoice? {
        switch self {
        case .none: .zero
        case .fifteen: .percent(15)
        case .eighteen: .percent(18)
        case .twenty: .percent(20)
        case .custom: nil
        }
    }

    var title: String {
        switch self {
        case .none: "0%"
        case .fifteen: "15%"
        case .eighteen: "18%"
        case .twenty: "20%"
        case .custom: "Custom"
        }
    }

    var spokenTitle: String {
        switch self {
        case .none: "No"
        case .fifteen: "15 percent"
        case .eighteen: "18 percent"
        case .twenty: "20 percent"
        case .custom: "Custom"
        }
    }
}
