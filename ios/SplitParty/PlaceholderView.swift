import SwiftUI

/// Temporary first screen. The app-shell Work Order replaces it with the real
/// navigation; it exists so the project builds and runs on a device, and so a
/// missing local setup step is visible instead of silent.
struct PlaceholderView: View {
    let target: FirestoreTarget

    private var hasFirebaseConfigFile: Bool {
        Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
    }

    var body: some View {
        VStack(spacing: 16) {
            Text("🎉").font(.system(size: 64))
            Text("Split Party").font(.largeTitle.bold())
            Text("iOS placeholder").foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 8) {
                Label(target.summary, systemImage: "cylinder.split.1x2")
                if case let .notConfigured(reason) = target {
                    Text(reason).font(.footnote).foregroundStyle(.orange)
                }
                if !hasFirebaseConfigFile {
                    Label("GoogleService-Info.plist missing (see ios/README.md)",
                          systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.orange)
                }
            }
            .font(.callout)
            .padding()
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
        }
        .padding()
    }
}

#Preview {
    PlaceholderView(target: .emulator(projectId: "demo-split-party", host: "192.168.1.20:8080"))
}
