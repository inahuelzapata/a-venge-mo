# convention

A rule the codebase follows — naming, patterns, and where things live.

## Apps and scripts use the same three Firestore target keys

What: iOS, Android and backend all choose Firestore with FIREBASE_TARGET (emulator|hosted), FIREBASE_PROJECT_ID and FIRESTORE_EMULATOR_HOST, each set in one git-ignored file: backend/.env, ios/Config/FirestoreTarget.local.xcconfig (optional #include? from Base.xcconfig into Info.plist), android/firestore.properties (into BuildConfig); missing values show "not configured" on the placeholder screen · Why: a demo needs phones and scripts on the same database, and the emulator keeps one database per project id, so the ids must match · Where: docs/firestore-target.md · Learned: phones use the laptop's LAN IP, scripts use 127.0.0.1, so the emulator binds 0.0.0.0
