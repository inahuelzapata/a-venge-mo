import SwiftUI

/// The party screen's total area (PoC-10, FR-3, FR-4). The host adds the total
/// (scan or keypad) and picks the tip; every member, host included, sees the
/// total, tip, headcount and their own share once a bill exists. Members never
/// get entry or tip controls.
/// Measured values: docs/design/visual-conventions.md ("Total area").
struct PartyTotalSection: View {
    let party: SplitParty
    let room: PartyRoomModel
    let usersById: [String: UserAccount]
    /// The host with the "Custom shares" switch on: member shares open the keypad.
    var canSetCustomShares = false

    @State private var sheet: TotalSheet?

    var body: some View {
        Group {
            if let split = party.split {
                VStack(spacing: Theme.totalSectionSpacing) {
                    ShareSummary(split: split, tip: party.tip, userId: room.userId)
                    MemberSharesList(split: split, usersById: usersById, isHost: room.isHost,
                                     onSetShare: canSetCustomShares
                                         ? { await room.setCustomShare(memberId: $0, cents: $1) } : nil,
                                     isSaving: room.isSaving)
                    if room.isHost {
                        TipChipRow(party: party, room: room, onCustom: { sheet = .customTip })
                        EditTotalButtons(onScan: { sheet = .scan }, onEnter: { sheet = .enterTotal },
                                         isDisabled: room.isSaving)
                    }
                }
            } else if room.isHost {
                AddTotalCard(onScan: { sheet = .scan }, onEnter: { sheet = .enterTotal })
            } else {
                TotalPlaceholder()
            }
        }
        .sheet(item: $sheet) { sheet in
            sheetContent(sheet)
        }
    }

    @ViewBuilder
    private func sheetContent(_ sheet: TotalSheet) -> some View {
        switch sheet {
        case .enterTotal:
            AmountEntryView(
                title: "Enter total", prompt: "What's the total on the receipt?", notice: nil,
                initialCents: party.billCents, input: .bill, confirmTitle: "Confirm total",
                onConfirm: { await room.confirmBill(cents: $0) })
                .accessibilityIdentifier("enterTotalScreen")
        case .customTip:
            AmountEntryView(
                title: "Custom tip", prompt: "How much tip, in dollars and cents?", notice: nil,
                initialCents: {
                    if case let .custom(amountCents) = party.tip, amountCents > 0 { return amountCents }
                    return nil
                }(),
                input: .customTip, confirmTitle: "Set tip",
                onConfirm: { await room.chooseTip(.custom(amountCents: $0)) })
                .accessibilityIdentifier("customTipScreen")
        case .scan:
            ScanAndConfirmFlow(room: room)
        }
    }
}

/// Which full-height step is open over the party screen.
enum TotalSheet: Identifiable, Hashable {
    /// The keypad, starting from the current bill.
    case enterTotal
    case customTip
    /// The camera, then the confirmation of what it found (same sheet).
    case scan

    var id: Self { self }
}

/// The scan and its confirmation step in ONE sheet: swapping sheets would add a
/// dismiss and a present (about a second) between capture and the detected
/// amount, eating into the 3 s budget.
private struct ScanAndConfirmFlow: View {
    let room: PartyRoomModel

    @Environment(\.dismiss) private var dismiss
    @State private var step: Step = .scanning

    private enum Step: Equatable {
        case scanning
        /// The keypad, pre-filled with a detected amount or carrying why not.
        case entry(detectedCents: Int?, notice: String?)
    }

    var body: some View {
        switch step {
        case .scanning:
            ReceiptScanView(
                onDetected: { step = .entry(detectedCents: $0, notice: nil) },
                onNoTotal: { step = .entry(detectedCents: nil, notice: $0) },
                onEnterInstead: { step = .entry(detectedCents: nil, notice: nil) },
                onCancel: { dismiss() })
        case let .entry(detectedCents, notice):
            AmountEntryView(
                title: detectedCents == nil ? "Enter total" : "Confirm total",
                prompt: detectedCents == nil ? "What's the total on the receipt?" : "Is this the receipt's total?",
                notice: notice, initialCents: detectedCents, input: .bill, confirmTitle: "Confirm total",
                onConfirm: { await room.confirmBill(cents: $0) })
                .accessibilityIdentifier(detectedCents == nil ? "enterTotalScreen" : "confirmTotalScreen")
        }
    }
}

// MARK: Add the total (host, no bill yet)

private struct AddTotalCard: View {
    let onScan: () -> Void
    let onEnter: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "receipt")
                .font(.system(size: 24))
                .foregroundStyle(Theme.brandBlue)
                .accessibilityHidden(true)
            Text("Add the total")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.primaryText)
                .accessibilityAddTraits(.isHeader)
            Text("Scan the receipt or type the amount.")
                .font(.system(size: 14))
                .foregroundStyle(Theme.secondaryText)
            EditTotalButtons(onScan: onScan, onEnter: onEnter, isDisabled: false)
                .padding(.top, 8)
        }
        .frame(maxWidth: .infinity, minHeight: 120)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("addTotalCard")
    }
}

/// "Scan receipt" and "Enter total", side by side. Shown on the empty card and,
/// once a bill exists, under the tip chips so the host can re-scan or re-type.
private struct EditTotalButtons: View {
    let onScan: () -> Void
    let onEnter: () -> Void
    let isDisabled: Bool

    var body: some View {
        HStack(spacing: Theme.totalButtonSpacing) {
            Button(action: onScan) {
                Label("Scan receipt", systemImage: "camera.viewfinder")
                    .totalActionLabel(foreground: .white, background: .black)
            }
            .accessibilityIdentifier("scanReceiptButton")
            Button(action: onEnter) {
                Label("Enter total", systemImage: "keyboard")
                    .totalActionLabel(foreground: Theme.primaryText, background: Theme.actionBackground)
            }
            .accessibilityIdentifier("enterTotalButton")
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
    }
}

private extension View {
    func totalActionLabel(foreground: Color, background: Color) -> some View {
        font(.system(size: 15, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: Theme.totalActionHeight)
            .background(Capsule().fill(background))
            .contentShape(Capsule())
    }
}

// MARK: Keypad entry

/// A full-height step with a large amount field on the decimal keypad. Amounts
/// are parsed straight to integer cents by `CentsInput`; an invalid amount shows
/// an inline message and writes nothing. A rejected write keeps the sheet open
/// with the error.
struct AmountEntryView: View {
    let title: String
    let prompt: String
    /// Shown above the field, e.g. why a scan landed here.
    let notice: String?
    let initialCents: Int?
    let input: CentsInput
    let confirmTitle: String
    /// Returns nil on success, or the error to show.
    let onConfirm: (Int) async -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var errorMessage: String?
    @State private var isSaving = false
    @FocusState private var isFieldFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let notice {
                    Label(notice, systemImage: "doc.text.magnifyingglass")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.primaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Theme.actionBackground))
                        .accessibilityIdentifier("entryNotice")
                }
                Text(prompt)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.secondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("$")
                        .font(.system(size: Theme.amountEntryFontSize * 0.6, weight: .bold))
                        .foregroundStyle(Theme.secondaryText)
                        .accessibilityHidden(true)
                    TextField("0.00", text: $text)
                        .font(.system(size: Theme.amountEntryFontSize, weight: .bold))
                        .monospacedDigit()
                        .keyboardType(.decimalPad)
                        .focused($isFieldFocused)
                        .accessibilityLabel("Amount in dollars")
                        .accessibilityIdentifier("amountField")
                        .onChange(of: text) { errorMessage = nil }
                }
                .padding(.horizontal, 18)
                .frame(minHeight: Theme.amountEntryFieldHeight)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Theme.card))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(errorMessage == nil ? Color.clear : Theme.errorText, lineWidth: 2))
                if let errorMessage {
                    Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.errorText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("amountError")
                }
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .background(Theme.background.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) {
                Button(confirmTitle, action: confirm)
                    .buttonStyle(PillButtonStyle())
                    .disabled(isSaving)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)
                    .accessibilityIdentifier("confirmAmountButton")
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .accessibilityIdentifier("cancelAmountButton")
                }
            }
        }
        .onAppear {
            if let initialCents { text = CentsInput.editableText(cents: initialCents) }
            isFieldFocused = true
        }
        .sensoryFeedback(.error, trigger: errorMessage) { _, new in new != nil }
    }

    private func confirm() {
        switch input.parse(text) {
        case let .failure(problem):
            errorMessage = problem.message
        case let .success(cents):
            isSaving = true
            Task {
                let failure = await onConfirm(cents)
                isSaving = false
                if let failure {
                    errorMessage = failure
                } else {
                    dismiss()
                }
            }
        }
    }
}
