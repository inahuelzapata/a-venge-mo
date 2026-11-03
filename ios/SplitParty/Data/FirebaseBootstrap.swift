import FirebaseCore
import FirebaseFirestore
import Foundation

/// Configures Firebase once, for the target chosen in `FirestoreTarget.local.xcconfig`
/// (docs/firestore-target.md), and hands back the Firestore instance to use.
///
/// - Emulator: works **without** `GoogleService-Info.plist`. The emulator accepts any
///   app id and only cares about the project id, so placeholder options are enough.
/// - Hosted: requires `GoogleService-Info.plist` from the same project as
///   `FIREBASE_PROJECT_ID`; a mismatch is reported instead of silently showing
///   another project's data.
enum FirebaseBootstrap {
    enum Outcome {
        case ready(Firestore)
        /// Setup is incomplete; `reason` is shown on the login screen.
        case failed(reason: String)
    }

    /// Placeholder app id for the emulator. Firebase validates only the format.
    private static let emulatorGoogleAppId = "1:000000000000:ios:0000000000000000000000"
    private static let emulatorSenderId = "000000000000"

    static func configure(target: FirestoreTarget, bundle: Bundle = .main) -> Outcome {
        switch target {
        case let .notConfigured(reason):
            return .failed(reason: reason)

        case let .emulator(projectId, host):
            let options = FirebaseOptions(googleAppID: emulatorGoogleAppId, gcmSenderID: emulatorSenderId)
            options.projectID = projectId
            configureAppIfNeeded(options)
            let firestore = Firestore.firestore()
            let settings = firestore.settings
            settings.host = host
            settings.isSSLEnabled = false
            applyMemoryOnlyCache(to: settings)
            firestore.settings = settings
            return .ready(firestore)

        case let .hosted(projectId):
            guard let plistPath = bundle.path(forResource: "GoogleService-Info", ofType: "plist"),
                  let options = FirebaseOptions(contentsOfFile: plistPath) else {
                return .failed(reason: "GoogleService-Info.plist is missing. Hosted needs it (see ios/README.md).")
            }
            guard options.projectID == projectId else {
                return .failed(reason: "GoogleService-Info.plist is for project \"\(options.projectID ?? "?")\" "
                    + "but FIREBASE_PROJECT_ID is \"\(projectId)\". Use the plist from the same project.")
            }
            configureAppIfNeeded(options)
            let firestore = Firestore.firestore()
            let settings = firestore.settings
            applyMemoryOnlyCache(to: settings)
            firestore.settings = settings
            return .ready(firestore)
        }
    }

    private static func configureAppIfNeeded(_ options: FirebaseOptions) {
        guard FirebaseApp.app() == nil else { return }
        FirebaseApp.configure(options: options)
    }

    /// No disk cache: the three demo phones must show what the database holds right
    /// now. A persisted cache would show stale rows after a reseed or while offline,
    /// where the work order asks for a visible error state instead.
    private static func applyMemoryOnlyCache(to settings: FirestoreSettings) {
        settings.cacheSettings = MemoryCacheSettings()
    }
}
