import SwiftUI

/// Every member's share under the extended split rule, on every phone in the
/// party (host and joinees, whatever their own "Custom shares" switch says): a
/// phone's switch never changes what a party is worth. Each row says whether the
/// member is on a custom amount or on the remainder (the equal split of what the
/// custom amounts leave). With Reduce Motion the amounts change without rolling.
///
/// On the host's phone with the switch on (`onSetShare` non-nil), tapping a row
/// opens an inline amount keypad under it to set, change or clear that member's
/// custom amount; everywhere else the rows are read-only.
struct MemberSharesList: View {
    let split: PartySplit
    let usersById: [String: UserAccount]
    /// The host's phone: the "amounts don't fit" message is worded for the host.
    let isHost: Bool
    /// Host with the "Custom shares" switch on only: writes one member's amount
    /// (nil clears it) and returns nil on success or the inline error.
    var onSetShare: ((_ memberId: String, _ cents: Int?) async -> String?)?
    /// A party write is in flight: rows don't open a new editor meanwhile.
    var isSaving = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var editingMemberId: String?

    private static let rollAnimation = Animation.spring(response: 0.45, dampingFraction: 0.85)

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Who pays what")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
                .accessibilityAddTraits(.isHeader)
            VStack(spacing: 0) {
                ForEach(split.memberIds, id: \.self) { memberId in
                    row(for: memberId)
                    if let onSetShare, editingMemberId == memberId {
                        CustomShareEditor(name: firstName(memberId), memberId: memberId,
                                          customCents: split.customShareCentsByMember[memberId],
                                          onSave: { await onSetShare(memberId, $0) },
                                          onClose: { setEditing(nil) })
                            .transition(.opacity)
                    }
                    if memberId != split.memberIds.last {
                        Divider()
                    }
                }
            }
            if let problem = split.customShareProblem {
                Label(isHost ? problem.message : "The host is fixing the custom amounts.",
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.errorText)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("customSharesProblem")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .animation(reduceMotion ? nil : Self.rollAnimation, value: split)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("memberShares")
    }

    @ViewBuilder
    private func row(for memberId: String) -> some View {
        let name = firstName(memberId)
        let kind = kind(of: memberId)
        let shareCents = split.share(of: memberId) ?? 0
        let content = MemberShareRow(name: name, shareCents: shareCents, kind: kind)
        if onSetShare != nil {
            Button { setEditing(editingMemberId == memberId ? nil : memberId) } label: { content }
                .buttonStyle(.plain)
                .disabled(isSaving)
                .memberShareAccessibility(name: name, memberId: memberId, shareCents: shareCents, kind: kind)
                .accessibilityHint(kind == .custom
                    ? "Double-tap to change or clear \(name)'s custom amount"
                    : "Double-tap to set a custom amount for \(name)")
        } else {
            content
                .memberShareAccessibility(name: name, memberId: memberId, shareCents: shareCents, kind: kind)
        }
    }

    private func setEditing(_ memberId: String?) {
        if reduceMotion {
            editingMemberId = memberId
        } else {
            withAnimation(.easeInOut(duration: 0.2)) { editingMemberId = memberId }
        }
    }

    private func firstName(_ memberId: String) -> String {
        usersById[memberId]?.firstName ?? memberId
    }

    private func kind(of memberId: String) -> MemberShareKind {
        guard split.hasCustomShares else { return .equal }
        return split.hasCustomShare(memberId) ? .custom : .remainder
    }
}

/// How a member's share was decided, shown under their name.
enum MemberShareKind: Equatable {
    /// Nobody has a custom amount: the plain equal split.
    case equal
    /// The host set this member's amount.
    case custom
    /// This member splits what the custom amounts leave.
    case remainder

    var label: String {
        switch self {
        case .equal: "Equal split"
        case .custom: "Custom amount"
        case .remainder: "Remainder"
        }
    }

    /// What VoiceOver adds after the amount.
    var spokenLabel: String {
        switch self {
        case .equal: "equal split"
        case .custom: "custom amount"
        case .remainder: "remainder, splits the rest equally"
        }
    }
}

/// One member: first name and how their share was decided, then the amount.
private struct MemberShareRow: View {
    let name: String
    let shareCents: Int
    let kind: MemberShareKind

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
                    .lineLimit(1)
                Label(kind.label, systemImage: kind == .custom ? "pencil" : "equal")
                    .labelStyle(.titleAndIcon)
                    .font(.system(size: 13, weight: kind == .custom ? .semibold : .regular))
                    .foregroundStyle(kind == .custom ? Theme.brandBlue : Theme.secondaryText)
            }
            Spacer(minLength: 8)
            Text(Money.format(cents: shareCents))
                .font(.system(size: 17, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.primaryText)
                .contentTransition(reduceMotion ? .identity : .numericText(value: Double(shareCents)))
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, minHeight: 44)
        .contentShape(Rectangle())
    }
}

private extension View {
    /// "Annie's share", "35 dollars, custom amount". Never just "Annie": that is
    /// her seat in the member row (which the host taps to remove her).
    func memberShareAccessibility(name: String, memberId: String, shareCents: Int,
                                  kind: MemberShareKind) -> some View {
        accessibilityElement(children: .ignore)
            .accessibilityLabel("\(name)'s share")
            .accessibilityValue("\(Money.spoken(cents: shareCents)), \(kind.spokenLabel)")
            .accessibilityIdentifier("memberShare-\(memberId)")
    }
}

/// The inline amount keypad under a member's row (host, switch on). Same entry
/// rules as the bill ($0.01 to $9,999.99, at most two decimals), parsed straight
/// to cents. "Back to equal" clears a custom amount. A refused amount keeps the
/// editor open with the reason; the party keeps its last valid state.
private struct CustomShareEditor: View {
    let name: String
    let memberId: String
    /// The member's current custom amount, nil when on the remainder.
    let customCents: Int?
    let onSave: (Int?) async -> String?
    let onClose: () -> Void

    @State private var text = ""
    @State private var errorMessage: String?
    @State private var isSaving = false
    @FocusState private var isFieldFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text("$")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Theme.secondaryText)
                    .accessibilityHidden(true)
                TextField("0.00", text: $text)
                    .font(.system(size: 28, weight: .bold))
                    .monospacedDigit()
                    .keyboardType(.decimalPad)
                    .focused($isFieldFocused)
                    .accessibilityLabel("Custom amount for \(name), in dollars")
                    .accessibilityIdentifier("customShareField")
                    .onChange(of: text) { errorMessage = nil }
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 52)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.actionBackground))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(errorMessage == nil ? Color.clear : Theme.errorText, lineWidth: 2))
            if let errorMessage {
                Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.errorText)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("customShareError")
            }
            HStack(spacing: 8) {
                Button("Set") { save() }
                    .editorButton(foreground: .white, background: .black)
                    .accessibilityLabel("Set \(name)'s amount")
                    .accessibilityIdentifier("setCustomShareButton")
                if customCents != nil {
                    Button("Back to equal") { clear() }
                        .editorButton(foreground: Theme.primaryText, background: Theme.card)
                        .accessibilityLabel("Put \(name) back on the equal split")
                        .accessibilityIdentifier("clearCustomShareButton")
                }
                Button("Cancel", action: onClose)
                    .editorButton(foreground: Theme.secondaryText, background: Theme.card)
                    .accessibilityIdentifier("cancelCustomShareButton")
            }
            .disabled(isSaving)
        }
        .padding(.bottom, 12)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("customShareEditor")
        .onAppear {
            if let customCents, customCents > 0 { text = CentsInput.editableText(cents: customCents) }
            isFieldFocused = true
        }
        .sensoryFeedback(.error, trigger: errorMessage) { _, new in new != nil }
    }

    private func save() {
        switch CentsInput.customShare.parse(text) {
        case let .failure(problem):
            errorMessage = problem.message
        case let .success(cents):
            write(cents, announcement: "\(name)'s share set to \(Money.spoken(cents: cents))")
        }
    }

    private func clear() {
        write(nil, announcement: "\(name) is back on the equal split")
    }

    private func write(_ cents: Int?, announcement: String) {
        isSaving = true
        Task {
            let failure = await onSave(cents)
            isSaving = false
            if let failure {
                errorMessage = failure
                UIAccessibility.post(notification: .announcement, argument: failure)
            } else {
                UIAccessibility.post(notification: .announcement, argument: announcement)
                onClose()
            }
        }
    }
}

private extension View {
    func editorButton(foreground: Color, background: Color) -> some View {
        font(.system(size: 15, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(Capsule().fill(background))
            .contentShape(Capsule())
            .buttonStyle(.plain)
    }
}
