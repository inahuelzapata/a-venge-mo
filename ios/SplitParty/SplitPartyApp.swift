import SwiftUI

@main
struct SplitPartyApp: App {
    @State private var appModel = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appModel)
                // Avatar photos (users.avatar URLs), fetched once and kept on the phone.
                .environment(\.avatarImageStore, AvatarImageStore.shared)
                // The reference screenshots are light; one look on every demo phone.
                .preferredColorScheme(.light)
        }
    }
}

/// Login until someone is signed in, then the tabbed shell.
struct RootView: View {
    @Environment(AppModel.self) private var appModel

    var body: some View {
        if let liveData = appModel.liveData {
            MainTabView()
                .environment(liveData)
                // A new identity per user resets tab selection and scroll position.
                .id(liveData.userId)
        } else {
            LoginView()
        }
    }
}
