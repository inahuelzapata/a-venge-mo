# Where GoogleService-Info.plist goes

The Firebase iOS configuration file is **not committed** (it is git-ignored).
It is needed for `FIREBASE_TARGET = hosted` (the default); the opt-in emulator works without it.

1. Firebase console → Project settings → Your apps → iOS app
   `com.splitparty.ios` → download `GoogleService-Info.plist`.
2. Put it at `ios/SplitParty/GoogleService-Info.plist`. That folder is
   synchronised with the Xcode project, so no project edit is needed.

With hosted selected and the file missing (or from a different project than
`FIREBASE_PROJECT_ID`), the login screen says so and sign-in stays disabled.
