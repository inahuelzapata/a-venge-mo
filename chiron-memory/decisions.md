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

## iOS app uses a memory-only Firestore cache

What: FirebaseBootstrap sets MemoryCacheSettings, and LiveDataStore shows an error card when a list is only fromCache: at once if NWPathMonitor reports no network, otherwise after a 4 s grace; on network return it calls disableNetwork/enableNetwork to skip Firestore's retry backoff · Why: the work order wants a visible error state offline and identical data on all demo phones; a disk cache would silently show stale rows instead · Where: ios/SplitParty/Data/FirebaseBootstrap.swift, ios/SplitParty/App/LiveDataStore.swift · Learned: Firestore listeners never error when offline, they just deliver cached snapshots, so "unreachable" must be derived from metadata.isFromCache

## Feed rows use display names, never "You"

What: Both apps render every feed row as "Payer paid Payee for memo" with display names, even for the signed-in user · Why: the checkpoint requires the 3 phones to show identical rows; "You" would differ per phone · Where: ios/SplitParty/UI/TransactionList.swift, docs/design/README.md

## Avatars are initials on a fixed colour per avatar key

What: users.avatar keys map to fixed colours (avatar_maxi #0087E0, avatar_annie #E5487D, avatar_christophe #2EAF6F, unknown #8E8E93) drawn with initials; no image assets · Why: no avatar art exists, and fixed values let Android match iOS exactly · Where: ios/SplitParty/UI/AvatarView.swift, docs/design/README.md

## iOS snapshot and UI tests run on an in-memory fixture, not Firestore

What: Screens read through the PaymentDataSource protocol; FixtureDataSource (Debug only) serves the seeded users and payments with ages relative to now; UI tests switch to it with -UITestFixture seeded|empty|unreachable, -UITestResetSession, -UITestInsertIncomingPaymentAfter · Why: tests need no Java/emulator and stay deterministic; relative ages keep "2 days ago" stable in snapshots · Where: ios/SplitParty/Data/FixtureDataSource.swift, ios/SplitParty/App/AppModel.swift UITestLaunch · Learned: the fixture must stay in step with backend/lib/seedData.js by hand
## Android emulator target uses its own FirebaseApp with the project id forced

What: For FIREBASE_TARGET=emulator the Android app creates a named FirebaseApp from google-services.json options (or placeholder options when the file is absent) with projectId set to FIREBASE_PROJECT_ID, then calls useEmulator; hosted uses the default app and refuses (Setup required screen) if the json's project_id differs · Why: the emulator keeps one database per project id, so the default app (project from google-services.json) would read a different, empty database than the seed script; and an emulator demo should not need a Firebase config file · Where: android/app/src/main/java/com/splitparty/android/data/firebase/FirebaseSetup.kt · Learned: the iOS shell needs the same project-id override for the emulator

## Android icons are local vector drawables drawn as SF Symbol look-alikes

What: Android's icons (house.fill, person.fill, magnifyingglass, qrcode, bubble.left, heart, tray, wifi.exclamationmark, exclamationmark.circle.fill, cylinder) are hand-drawn res/drawable/ic_*.xml vectors sized so their ink matches the SF Symbols in the iOS captures · Why: SF Symbols can't ship on Android and the Compose icon packs are discontinued; glyph shape is a documented allowed difference, size and position are not · Where: android/app/src/main/res/drawable/, docs/design/visual-conventions.md

## Android DI uses Koin (DSL, no annotations) rather than Hilt

What: Koin 4.2 with the constructor DSL (singleOf/factoryOf/viewModelOf) and koinViewModel { parametersOf(userId) } for user-scoped ViewModels; KoinModulesTest verifies the graph · Why: the user asked for Koin; the DSL needs no KSP/kapt compiler plugin, which keeps the AGP 9 built-in-Kotlin build simple · Where: android/app/src/main/java/com/splitparty/android/di/AppModules.kt, android/app/src/test/java/com/splitparty/android/di/KoinModulesTest.kt · Learned: verify() needs extraTypes for androidContext() (Context) and parametersOf values (String)

## Android screenshot tests use Paparazzi 2.0 alpha on the stateless screens

What: Paparazzi snapshots every stateless XScreen in each UI state (plus a 1.5x font-scale pass) on DeviceConfig.PIXEL_5; goldens are committed in android/app/src/test/snapshots/images and `./gradlew check` depends on verifyPaparazziDebug · Why: catches visual regressions the semantics-based Robolectric tests can't; Paparazzi 2.0.0-alpha05.1 is the first release with AGP 9 support (1.3.x stops at AGP 8) · Where: android/app/src/test/java/com/splitparty/android/screenshots/Snapshots.kt, android/app/build.gradle.kts · Learned: plain testDebugUnitTest only renders snapshots and never compares them; stateless screens have no page background, so the shared `screen {}` helper wraps them in SplitPartyTheme + Brand.Page like the app root does

## Brand assets are generated from brand/ by one Swift script

What: The Aven-mo mark is hand-traced (measured coordinates) from brand/source/aven-mo-mark-source.png into brand/aven-mo-mark.svg, and `swift brand/generate-brand-assets.swift` writes the SVG, the iOS AppIcon and AvenMoMark imageset, and the Android mark, launcher foreground and background vectors; it prints a trace-vs-source overlap score (0.958 at first run) · Why: both platforms must trace back to one place, and this Mac has no ImageMagick/rsvg/Pillow, so CoreGraphics in a Swift script is the dependency-free renderer · Where: brand/generate-brand-assets.swift, docs/design/README.md "Brand mark" · Learned: never edit the generated files; change the geometry or colours in the script and re-run it

## iOS icon master is the user's glossy icon upscaled, not rebuilt

What: brand/aven-mo-icon-1024.png is the supplied 500×500 glossy icon cropped to its rounded square, upscaled to 1024 on navy #002458, with pixels below alpha 250 (the background-removal halo) painted navy so the PNG is opaque · Why: the user chose upscaling over rebuilding the glossy look from the vector; iOS rejects icons with transparency · Where: brand/generate-brand-assets.swift iconMaster() · Learned: it is slightly soft; a real 1024 export from the designer should replace brand/source/aven-mo-icon-glossy-source.png when available

## Android bundles Inter as the stand-in for SF Pro

What: Every Android text role uses Inter 4.1 (SIL OFL 1.1, static Regular/Medium/SemiBold/Bold in res/font, licence in assets/licenses/Inter-OFL.txt) at the iOS sizes and weights, with SF's 1.19 line height and per-role negative tracking tuned until line widths match the captures · Why: Apple's SF Pro may not be bundled on Android; Inter is open-licensed and closest in metrics · Where: android/app/src/main/java/com/splitparty/android/ui/theme/Type.kt · Learned: Inter sets wider than SF unless tracked about -0.027em (<=18) / -0.035em (20) / -0.03em (display)

## Android lays out against the reference iPhone's safe area as a minimum

What: Content starts at max(system top inset, 62 dp) and the floating tab bar sits 4 dp above max(navigation inset, 34 dp), the iPhone 18 Pro safe area · Why: keeps header and bar at the same height as the iPhone when phones are placed side by side, while still clearing cutouts and gesture areas · Where: android/app/src/main/java/com/splitparty/android/ui/theme/SplitPartyTheme.kt (topInset/bottomInset, LocalMinimumInsets)

## Android parity renders are Paparazzi goldens at full device resolution

What: ParityScreenshotTest renders one Android image per iOS capture (same state, the iOS fixture data) with useDeviceResolution = true, and docs/design/capture/compare.py copies them to docs/design/android-captures/ and builds the comparisons · Why: deterministic and re-creatable by any reviewer without phones or a database; regular goldens stay as small thumbnails · Where: android/app/src/test/java/com/splitparty/android/screenshots/ParityScreenshotTest.kt · Learned: Paparazzi scales snapshots to 1000 px unless useDeviceResolution is set, too coarse for 1 dp checks
