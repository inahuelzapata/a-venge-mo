import Foundation

/// Which Firestore the app talks to: the hosted project or the emulator started
/// from `backend/`. Values come from Info.plist, which Xcode fills from
/// `Config/Base.xcconfig` and the git-ignored `Config/FirestoreTarget.local.xcconfig`.
/// The same keys are used by Android and the backend scripts (docs/firestore-target.md).
enum FirestoreTarget: Equatable {
    case emulator(projectId: String, host: String)
    case hosted(projectId: String)
    /// The local config is missing or incomplete; `reason` says what to fix.
    case notConfigured(reason: String)

    static func fromBundle(_ bundle: Bundle = .main) -> FirestoreTarget {
        func value(_ key: String) -> String {
            (bundle.object(forInfoDictionaryKey: key) as? String)?
                .trimmingCharacters(in: .whitespaces) ?? ""
        }
        return resolve(
            target: value("FirebaseTarget"),
            projectId: value("FirebaseProjectId"),
            emulatorHost: value("FirestoreEmulatorHost")
        )
    }

    static func resolve(target: String, projectId: String, emulatorHost: String) -> FirestoreTarget {
        let setupHint = "Copy ios/Config/FirestoreTarget.example.xcconfig to FirestoreTarget.local.xcconfig."
        guard !target.isEmpty else {
            return .notConfigured(reason: "FIREBASE_TARGET is not set. \(setupHint)")
        }
        guard !projectId.isEmpty else {
            return .notConfigured(reason: "FIREBASE_PROJECT_ID is not set. \(setupHint)")
        }
        switch target {
        case "emulator":
            guard !emulatorHost.isEmpty else {
                return .notConfigured(reason: "FIRESTORE_EMULATOR_HOST is not set (use <laptop LAN IP>:8080).")
            }
            return .emulator(projectId: projectId, host: emulatorHost)
        case "hosted":
            return .hosted(projectId: projectId)
        default:
            return .notConfigured(reason: "FIREBASE_TARGET=\"\(target)\" is invalid (use emulator or hosted).")
        }
    }

    var summary: String {
        switch self {
        case let .emulator(projectId, host): return "Emulator \(host) (project \(projectId))"
        case let .hosted(projectId): return "Hosted project \(projectId)"
        case .notConfigured: return "Firestore target not configured"
        }
    }
}
