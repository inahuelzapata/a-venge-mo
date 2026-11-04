# convention

A rule the codebase follows — naming, patterns, and where things live.

## Apps and scripts use the same three Firestore target keys

What: iOS, Android and backend all choose Firestore with FIREBASE_TARGET (emulator|hosted), FIREBASE_PROJECT_ID and FIRESTORE_EMULATOR_HOST, each set in one git-ignored file: backend/.env, ios/Config/FirestoreTarget.local.xcconfig (optional #include? from Base.xcconfig into Info.plist), android/firestore.properties (into BuildConfig); missing values show "not configured" on the placeholder screen · Why: a demo needs phones and scripts on the same database, and the emulator keeps one database per project id, so the ids must match · Where: docs/firestore-target.md · Learned: phones use the laptop's LAN IP, scripts use 127.0.0.1, so the emulator binds 0.0.0.0

## Both app shells match docs/design and render identical feed rows

What: The visual reference is docs/design/home-feed-reference-1.jpg (top of Home) and -2.jpg (scrolled feed); feed rows always use display names (never "You") so every phone shows the same rows; avatar keys map to fixed initial circles (avatar_maxi #3D7BE0, avatar_annie #E86A5C, avatar_christophe #2FA37A, unknown grey); relative time reads "just now / N minutes|hours|days ago / Oct 27"; brand blue #0B5FD6 on page grey #F2F3F5 · Why: the checkpoint compares 3 phones (iOS and Android mixed) row by row, and the apps share no code · Where: android/app/src/main/java/com/splitparty/android/ui/components/, docs/design/
