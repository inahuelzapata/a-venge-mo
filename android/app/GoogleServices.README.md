# Where google-services.json goes

The Firebase Android configuration file is **not committed** (it is git-ignored).
The **hosted** Firestore target (the default) needs it; the opt-in emulator target runs without it.

1. Firebase console → Project settings → Your apps → Android app with package
   `com.splitparty.android` → download `google-services.json`. Use the same
   project as `FIREBASE_PROJECT_ID` in `android/firestore.properties`.
2. Put it at `android/app/google-services.json`, then rebuild.

If the hosted target is selected and the file is missing (or belongs to another
project), the app opens on a "Setup required" screen instead of failing silently.
