# decision

A choice made and the reasoning behind it — the path taken over the alternatives.

## Join-code uniqueness via a lock document keyed by the code

What: A party's emoji join code is reserved by writing joinCodes/{merchantId}_{e1e2e3} in the same Firestore transaction as the party; a lock whose expiresAt has passed counts as free and is overwritten · Why: Firestore has no unique constraints, and the PoC has no server job to clean up expired parties, so a doc id plus a transaction (which aborts on concurrent change) is the only race-free guarantee · Where: docs/firestore-schema.md (Join-code uniqueness), backend/lib/schema.js joinCodeLockId · Learned: in Firestore, enforce uniqueness with a doc whose id is the unique value

## Users are keyed by username

What: users/{username} — the doc id is the username, which is therefore immutable · Why: doc ids are unique for free, and username-only login becomes a single get() with no extra index collection · Where: docs/firestore-schema.md (users)

## Per-person share is never stored; every client computes it

What: Shares come from billCents, tip and memberIds.length with integer math: floor division, leftover cents to the host (memberIds[0]), percent tip rounded half up as floor((bill*pct+50)/100) · Why: a stored share would go stale as people join, and every phone must show the same number · Where: backend/lib/split.js (reference port for iOS/Android), docs/firestore-schema.md (Split rule)

## Party payments use deterministic transaction ids

What: When the host pays, each joinee's payment is written to transactions/{partyId}_{payerId} with source "party"; seeded feed entries are seed_tx_NN with source "seed" · Why: a retried Pay can never charge twice, and reset can tell seed data from party data · Where: backend/lib/schema.js partyTransactionId, backend/lib/ops.js reset

## Android emulator target uses its own FirebaseApp with the project id forced

What: For FIREBASE_TARGET=emulator the Android app creates a named FirebaseApp from google-services.json options (or placeholder options when the file is absent) with projectId set to FIREBASE_PROJECT_ID, then calls useEmulator; hosted uses the default app and refuses (Setup required screen) if the json's project_id differs · Why: the emulator keeps one database per project id, so the default app (project from google-services.json) would read a different, empty database than the seed script; and an emulator demo should not need a Firebase config file · Where: android/app/src/main/java/com/splitparty/android/data/firebase/FirebaseSetup.kt · Learned: the iOS shell needs the same project-id override for the emulator

## Android icons are local vector drawables, not the Compose icon packs

What: The app's 9 icons (home, person, comment, heart, more, search, QR) are Material icon paths in app/src/main/res/drawable/ic_*.xml used via painterResource · Why: material-icons-core/extended are discontinued at 1.7.8 and no longer managed by the Compose BOM · Where: android/app/src/main/res/drawable/

## Android DI uses Koin (DSL, no annotations) rather than Hilt

What: Koin 4.2 with the constructor DSL (singleOf/factoryOf/viewModelOf) and koinViewModel { parametersOf(userId) } for user-scoped ViewModels; KoinModulesTest verifies the graph · Why: the user asked for Koin; the DSL needs no KSP/kapt compiler plugin, which keeps the AGP 9 built-in-Kotlin build simple · Where: android/app/src/main/java/com/splitparty/android/di/AppModules.kt, android/app/src/test/java/com/splitparty/android/di/KoinModulesTest.kt · Learned: verify() needs extraTypes for androidContext() (Context) and parametersOf values (String)

## Android screenshot tests use Paparazzi 2.0 alpha on the stateless screens

What: Paparazzi snapshots every stateless XScreen in each UI state (plus a 1.5x font-scale pass) on DeviceConfig.PIXEL_5; goldens are committed in android/app/src/test/snapshots/images and `./gradlew check` depends on verifyPaparazziDebug · Why: catches visual regressions the semantics-based Robolectric tests can't; Paparazzi 2.0.0-alpha05.1 is the first release with AGP 9 support (1.3.x stops at AGP 8) · Where: android/app/src/test/java/com/splitparty/android/screenshots/Snapshots.kt, android/app/build.gradle.kts · Learned: plain testDebugUnitTest only renders snapshots and never compares them; stateless screens have no page background, so the shared `screen {}` helper wraps them in SplitPartyTheme + Brand.Page like the app root does
