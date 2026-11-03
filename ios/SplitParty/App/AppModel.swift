import Foundation
import Observation

/// Top-level app state: which database we talk to and who is signed in.
@MainActor
@Observable
final class AppModel {
    let target: FirestoreTarget
    /// Nil when setup is incomplete; `setupProblem` then says why.
    let repository: PaymentRepository?
    let setupProblem: String?

    private(set) var signedInUserId: String?
    /// Live listeners for the signed-in session. Recreated on every sign-in so a
    /// new user never sees the previous user's Me tab for a moment.
    private(set) var liveData: LiveDataStore?

    @ObservationIgnored private let sessionStore: SessionStore

    init(target: FirestoreTarget = .fromBundle(), sessionStore: SessionStore = SessionStore()) {
        self.target = target
        self.sessionStore = sessionStore
        switch FirebaseBootstrap.configure(target: target) {
        case let .ready(firestore):
            repository = PaymentRepository(firestore: firestore)
            setupProblem = nil
        case let .failed(reason):
            repository = nil
            setupProblem = reason
        }
        // Stay signed in across restarts: reopen straight on Home for the saved user.
        if let repository, let savedUserId = sessionStore.signedInUserId {
            startSession(userId: savedUserId, repository: repository)
        }
    }

    /// Returns a message for the inline error, or nil on success.
    func signIn(username: String) async -> String? {
        guard let repository else { return setupProblem }
        do {
            let account = try await repository.signIn(username: username)
            sessionStore.save(userId: account.id)
            startSession(userId: account.id, repository: repository)
            return nil
        } catch PaymentRepository.LoginError.unknownUsername {
            return "No account called \"\(username.trimmingCharacters(in: .whitespaces))\". Try Maxi, Annie or Christophe."
        } catch {
            return "Can't reach the database. Check your connection and try again."
        }
    }

    func logOut() {
        sessionStore.clear()
        liveData?.stop()
        liveData = nil
        signedInUserId = nil
    }

    private func startSession(userId: String, repository: PaymentRepository) {
        liveData?.stop()
        signedInUserId = userId
        liveData = LiveDataStore(userId: userId, repository: repository)
    }
}
