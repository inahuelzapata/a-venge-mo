import SwiftUI

/// Username-only sign-in (no password). The seeded accounts are Maxi, Annie and
/// Christophe; any case works.
struct LoginView: View {
    @Environment(AppModel.self) private var appModel

    @State private var username = ""
    @State private var errorMessage: String?
    @State private var isSigningIn = false
    @FocusState private var isUsernameFocused: Bool

    private var trimmedUsername: String {
        username.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSubmit: Bool {
        !trimmedUsername.isEmpty && !isSigningIn && appModel.setupProblem == nil
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 8) {
                Text("Split Party")
                    .font(.system(size: 40, weight: .bold))
                    .foregroundStyle(Theme.brandBlue)
                Text("Sign in with your username")
                    .font(.system(size: 17))
                    .foregroundStyle(Theme.secondaryText)
            }
            .padding(.bottom, 32)

            VStack(alignment: .leading, spacing: 12) {
                TextField("Username", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textContentType(.username)
                    .submitLabel(.go)
                    .focused($isUsernameFocused)
                    .onSubmit(submit)
                    .onChange(of: username) { errorMessage = nil }
                    .font(.system(size: 18))
                    .padding(.horizontal, 20)
                    .frame(height: 56)
                    .background(Capsule().fill(Theme.card))
                    .overlay(Capsule().stroke(errorMessage == nil ? Color.clear : Theme.errorText, lineWidth: 1.5))
                    .accessibilityIdentifier("usernameField")

                if let errorMessage {
                    Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.errorText)
                        .padding(.horizontal, 8)
                        .accessibilityIdentifier("loginError")
                }

                Button(action: submit) {
                    if isSigningIn {
                        ProgressView().tint(.white)
                    } else {
                        Text("Sign in")
                    }
                }
                .buttonStyle(PillButtonStyle())
                .disabled(!canSubmit)
                .padding(.top, 8)
            }
            .padding(.horizontal, 24)

            Spacer()
            SetupStatusFooter()
        }
        .background(Theme.background.ignoresSafeArea())
        .onAppear { isUsernameFocused = true }
    }

    private func submit() {
        // The keyboard's Go key bypasses the disabled button, so check again here.
        guard canSubmit else { return }
        isSigningIn = true
        let enteredUsername = username
        Task {
            errorMessage = await appModel.signIn(username: enteredUsername)
            isSigningIn = false
        }
    }
}

/// Which database the app is pointed at, and what to fix if setup is incomplete.
/// Shown on the login screen so a misconfigured demo phone is obvious at once.
private struct SetupStatusFooter: View {
    @Environment(AppModel.self) private var appModel

    var body: some View {
        VStack(spacing: 6) {
            Label(appModel.target.summary, systemImage: "cylinder.split.1x2")
                .foregroundStyle(Theme.secondaryText)
            if let setupProblem = appModel.setupProblem {
                Text(setupProblem)
                    .foregroundStyle(.orange)
                    .multilineTextAlignment(.center)
            }
        }
        .font(.footnote)
        .padding(.horizontal, 24)
        .padding(.bottom, 16)
    }
}
