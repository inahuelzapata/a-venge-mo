import SwiftUI

@main
struct SplitPartyApp: App {
    var body: some Scene {
        WindowGroup {
            PlaceholderView(target: FirestoreTarget.fromBundle())
        }
    }
}
