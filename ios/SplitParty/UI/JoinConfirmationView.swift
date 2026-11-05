import SwiftUI
import UIKit

/// Before joining, the joinee picks the host's code out of four (PoC-9): proof
/// they are at the same table. Three wrong picks lock that party for them; the
/// host is never told. Measured values: docs/design/visual-conventions.md
/// ("Code confirmation").
struct JoinConfirmationView: View {
    let partyId: String
    let onJoined: () -> Void
    let onBackToHome: () -> Void

    @Environment(LiveDataStore.self) private var liveData
    @State private var room: PartyRoomModel?
    /// The third wrong pick is still shaking: the locked state waits for it.
    @State private var isFinalShakePlaying = false

    var body: some View {
        let hub = liveData.parties
        Group {
            if let room, let party = room.party {
                content(party: party, hub: hub)
            } else if room?.hasLoaded == true {
                PartyClosedView(title: "This party isn't available", message: "It may have been cleared.",
                                systemImage: "questionmark.circle", onBackToHome: onBackToHome)
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Join Split Party")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if room == nil {
                room = PartyRoomModel(partyId: partyId, userId: hub.userId, repository: hub.repository,
                                      connection: hub.connection)
            }
        }
        .onDisappear { room?.stop(); room = nil }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("joinConfirmationScreen")
        // Lets UI tests tell parties apart, as on the party screen.
        .accessibilityValue(partyId)
    }

    @ViewBuilder
    private func content(party: SplitParty, hub: PartyHub) -> some View {
        let userId = liveData.userId
        let hostName = liveData.usersById[party.hostId]?.firstName ?? party.hostId
        if party.isMember(userId) {
            // Already in (e.g. joined a moment ago): nothing to confirm.
            Color.clear.onAppear(perform: onJoined)
        } else if party.isLockedOut(userId) && !isFinalShakePlaying {
            PartyClosedView(title: "Too many wrong codes",
                            message: "You can't join \(hostName)'s party any more. "
                                + "Ask them to start a new one if you're at the same table.",
                            systemImage: "lock.fill", onBackToHome: onBackToHome)
        } else if !party.isActive(at: hub.now) {
            PartyClosedView(title: party.isExpired(at: hub.now) ? "This party has expired" : "This party is closed",
                            message: "There's nothing to join.", systemImage: "clock.badge.xmark",
                            onBackToHome: onBackToHome)
        } else {
            CodeChoices(party: party, userId: userId, hostName: hostName,
                        hostUser: liveData.usersById[party.hostId], merchantName: hub.merchantName,
                        repository: hub.repository, isFinalShakePlaying: $isFinalShakePlaying, onJoined: onJoined)
        }
    }
}

/// The four options, the wrong-pick feedback and the join itself.
private struct CodeChoices: View {
    let party: SplitParty
    let userId: String
    let hostName: String
    let hostUser: UserAccount?
    let merchantName: String
    let repository: any PaymentDataSource
    @Binding var isFinalShakePlaying: Bool
    let onJoined: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var options: [JoinCode] = []
    @State private var shakingOption: JoinCode?
    @State private var shakeProgress: CGFloat = 0
    @State private var wrongPickCount = 0
    @State private var errorMessage: String?
    @State private var isJoining = false

    /// Long enough for the shake to finish before the options move.
    private static let reshuffleDelay: Duration = .milliseconds(450)

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.partySectionSpacing) {
                VStack(spacing: 10) {
                    AvatarView(avatarKey: hostUser?.avatarKey ?? "", displayName: hostUser?.displayName ?? hostName,
                               size: Theme.memberAvatarSize)
                    Text("Join \(hostName)'s party at \(merchantName)")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Theme.primaryText)
                        .multilineTextAlignment(.center)
                    Text("Pick the code shown on \(hostName)'s phone")
                        .font(.system(size: 16))
                        .foregroundStyle(Theme.secondaryText)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 12)
                .padding(.horizontal, 24)
                .accessibilityElement(children: .combine)

                VStack(spacing: Theme.optionSpacing) {
                    ForEach(options, id: \.self) { option in
                        optionButton(option)
                    }
                }
                .padding(.horizontal, Theme.screenGutter)
                .animation(reduceMotion ? nil : .spring(response: 0.4, dampingFraction: 0.8), value: options)

                if let errorMessage {
                    Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.errorText)
                        .accessibilityIdentifier("joinCodeError")
                }
                if isJoining {
                    ProgressView("Joining…")
                }
            }
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .sensoryFeedback(.error, trigger: wrongPickCount)
        .onAppear {
            // A new random order every time the screen opens.
            if options.isEmpty { options = JoinCodeChoices.make(for: party.joinCode) }
        }
    }

    private func optionButton(_ option: JoinCode) -> some View {
        Button { pick(option) } label: {
            HStack(spacing: 18) {
                ForEach(Array(option.emojis.enumerated()), id: \.offset) { _, emoji in
                    Text(emoji).font(.system(size: Theme.optionEmojiFontSize))
                }
            }
            .frame(maxWidth: .infinity, minHeight: Theme.optionHeight)
            .background(RoundedRectangle(cornerRadius: Theme.codeTileCornerRadius, style: .continuous)
                .fill(Theme.card))
            .overlay(RoundedRectangle(cornerRadius: Theme.codeTileCornerRadius, style: .continuous)
                .stroke(shakingOption == option ? Theme.errorText : .clear, lineWidth: 2))
            .contentShape(RoundedRectangle(cornerRadius: Theme.codeTileCornerRadius, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isJoining)
        .modifier(ShakeEffect(progress: shakingOption == option ? shakeProgress : 0))
        .accessibilityLabel(option.spokenNames)
        .accessibilityIdentifier("codeOption")
    }

    private func pick(_ option: JoinCode) {
        guard !isJoining else { return }
        if option == party.joinCode {
            join()
        } else {
            rejectWrongPick(option)
        }
    }

    private func join() {
        isJoining = true
        errorMessage = nil
        Task {
            do {
                try await repository.joinParty(id: party.id, userId: userId)
                // The joining phone's own "you're in" haptic. Played here, not with
                // sensoryFeedback, because onJoined replaces this screen at once.
                // Haptics never depend on Reduce Motion.
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onJoined()
            } catch {
                errorMessage = (error as? PartyActionError ?? .unreachable).joinMessage
            }
            isJoining = false
        }
    }

    /// Shake + error haptic + message, count the attempt on the party, reshuffle.
    /// The third wrong attempt shows up in the party document and the parent
    /// switches to the locked state.
    private func rejectWrongPick(_ option: JoinCode) {
        wrongPickCount += 1
        let attemptsLeft = PartySchema.maxWrongCodeAttempts - party.wrongAttempts(by: userId) - 1
        errorMessage = attemptsLeft > 0
            ? "That's not \(hostName)'s code. \(attemptsLeft) \(attemptsLeft == 1 ? "try" : "tries") left."
            : "That's not \(hostName)'s code."
        Task { try? await repository.recordWrongCode(partyId: party.id, userId: userId) }

        shakingOption = option
        shakeProgress = 0
        if !reduceMotion {
            // The last attempt shakes too; the locked state shows after it.
            if attemptsLeft <= 0 { isFinalShakePlaying = true }
            withAnimation(.linear(duration: 0.4)) { shakeProgress = 1 }
        }
        Task {
            try? await Task.sleep(for: Self.reshuffleDelay)
            isFinalShakePlaying = false
            shakingOption = nil
            shakeProgress = 0
            options = JoinCodeChoices.make(for: party.joinCode, previous: options)
        }
    }
}

/// Side-to-side shake: `progress` 0 → 1 runs `Theme.shakeCount` swings.
private struct ShakeEffect: GeometryEffect {
    var progress: CGFloat

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let offset = Theme.shakeDistance * sin(progress * .pi * 2 * Theme.shakeCount)
        return ProjectionTransform(CGAffineTransform(translationX: offset, y: 0))
    }
}

extension PartyActionError {
    /// Wording for a failed join on the confirmation screen.
    var joinMessage: String {
        switch self {
        case .offline, .unreachable: "Couldn't join. Check your connection and try again."
        case .rejected: "The database refused the join. Try again."
        case .partyNoLongerOpen: "This party is no longer open."
        case .lockedOut: "Too many wrong codes for this party."
        case .notReadyToPay: "This party is no longer open."
        }
    }
}
