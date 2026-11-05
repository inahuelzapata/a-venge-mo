# convention

A rule the codebase follows — naming, patterns, and where things live.

## Apps and scripts use the same three Firestore target keys

What: iOS, Android and backend all choose Firestore with FIREBASE_TARGET (hosted by default in every template; emulator is opt-in), FIREBASE_PROJECT_ID and FIRESTORE_EMULATOR_HOST, each set in one git-ignored file: backend/.env, ios/Config/FirestoreTarget.local.xcconfig (optional #include? from Base.xcconfig into Info.plist), android/firestore.properties (into BuildConfig); missing values show "not configured" on screen (Android placeholder screen, iOS login footer) · Why: a demo needs phones and scripts on the same database, and the emulator keeps one database per project id, so the ids must match · Where: docs/firestore-target.md · Learned: phones use the laptop's LAN IP, scripts use 127.0.0.1, so the emulator binds 0.0.0.0

## The iOS captures are the visual contract for both apps

What: docs/design/ios-captures/ (iOS Debug fixture, iPhone 18 Pro simulator @3x, one PNG per screen and state) define the look; docs/design/visual-conventions.md holds the measured values and the only allowed platform differences; Android reproduces them and docs/design/comparisons/ shows iOS|Android side by side plus 50% overlays · Why: the shells share no code and are demoed side by side, so review needs an objective reference, not a judgement call · Where: docs/design/, docs/design/capture/ · Learned: on an iOS UI change, re-run capture-ios.sh first, then update the note, then Android

## Feed rows read identically on both platforms

What: Feed sentences use display names (never "You"), relative times use iOS RelativeDateTimeFormatter wording ("Just now", "3 hours ago", "2 weeks ago"), and the last line of a sentence never holds a lone word (Android moves the previous word down, as iOS text layout does) · Why: the demo puts three mixed phones next to each other and compares rows line by line · Where: android/app/src/main/java/com/splitparty/android/ui/components/FeedRow.kt, android/.../format/RelativeTime.kt, ios/SplitParty/UI/TransactionList.swift

## Android screens: one UiState, onAction, Route/Screen split, fakes in tests

What: each ViewModel exposes one immutable StateFlow<XUiState> and an onAction(XAction) entry point; XRoute resolves the ViewModel (koinViewModel as a default parameter) and XScreen(state, onAction, modifier) is stateless with @Previews; ViewModel tests use hand-written fakes from app/src/test/.../testing/Fakes.kt (no mocking library), Turbine and MainDispatcherRule; Compose UI tests render XScreen under Robolectric · Why: matches current Android guidance (UDF, state hoisting) and keeps screens testable without a device · Where: android/app/src/main/java/com/splitparty/android/ui/home/, android/README.md "Adding a screen"

## Reduced motion through LocalReduceMotion

What: Every Split Party animation (entrances, pop-in, bob, spring, pulse, sheen, shake) checks LocalReduceMotion, which defaults to !ValueAnimator.areAnimatorsEnabled() (animator scale 0 / Remove animations); haptics never depend on it. Paparazzi's screen() helper provides it as true so goldens show settled states · Where: android ui/theme/Motion.kt, ui/components/PartyMotion.kt, app/src/test/.../screenshots/Snapshots.kt

## Android on-device tests host MainShell over the shared fakes

What: Espresso/Compose tests in app/src/androidTest/.../e2e use PartyHarness. It restarts Koin (stopping the app's real modules first) with the fakes from app/src/sharedTest (FakePartyRepository and others, shared with the JVM tests via `kotlin.srcDir`) plus the real domain and ViewModel modules, then `setContent { MainShell(userId) }` in a ComponentActivity. They don't launch MainActivity, because FirebaseConnection.Ready needs a real FirebaseFirestore. Run with `./gradlew connectedDebugAndroidTest` (Orchestrator, clearPackageData, animations off) · Why: hermetic and deterministic, yet real CameraX, runtime permissions, system back and the bundled ML Kit model · Where: android app/src/androidTest, app/src/sharedTest, app/build.gradle.kts · Learned: with AGP 9's built-in Kotlin, `java.srcDir` isn't compiled as Kotlin; use `kotlin.srcDir`
## iOS party fixture and capture names match the Android parity renders

What: In Debug, -UITestFriendParty <host> (with -UITestFriendPartyMembers, -UITestFriendPartyAge, -UITestFriendPartyAfter) and -UITestFriendsJoinEvery drive the FixtureDataSource parties, which always use the code 🍕🍺🎉; ReferenceCaptureUITests saves join-banner, join-confirm, party-one-member, party-three-members, party-expired and home-demo-off under the same names as Android's ParityScreenshotTest · Why: UI tests and captures need a known correct option, and compare.py pairs iOS and Android images by file name · Where: ios/SplitParty/App/AppModel.swift (UITestLaunch), ios/SplitParty/Data/FixtureDataSource.swift, ios/SplitPartyUITests/ReferenceCaptureUITests.swift

## iOS live check of the total slice runs as two gated UI tests on two simulators

What: ios/SplitPartyUITests/LiveTotalUITests.swift is skipped unless TEST_RUNNER_LIVE_PARTY_ROLE (host|member) and TEST_RUNNER_LIVE_FIRESTORE_PROJECT are set. Build once (build-for-testing), fresh-install the app on two simulators, then run test-without-building with role host (Maxi) on one and, ~20 s later, member (Annie) on the other. They drive bill, 18%/20% tips, removal, rejoin and leave against the configured Firestore target and read the party document over the REST API (rules allow public reads) to check the stored billCents, tip and memberIds · Why: proves the deployed rules accept every write of the slice and two phones agree, without Java/emulator · Where: ios/SplitPartyUITests/LiveTotalUITests.swift · Learned: it creates a real party at Tony's in the target project (left to expire); needs the git-ignored FirestoreTarget.local.xcconfig and GoogleService-Info.plist copied into the checkout
## The Android Pay transaction is tested against the rules emulator with SettleRulesE2ETest

What: app/src/androidTest/.../e2e/SettleRulesE2ETest runs the real FirestorePartyRepository on an Android emulator against the Firestore emulator loaded with backend/firestore.rules (project demo-split-party-settle). It seeds and clears through the emulator REST API with `Bearer owner` (rules bypassed) over a raw socket, because the app's network policy forbids cleartext HTTP; the app's own writes go through the rules. It is skipped unless the `settleEmulator` instrumentation argument is set. Run `ANDROID_SERIAL=emulator-5554 android/scripts/settle-rules-e2e.sh` (Java 21 on PATH, `npm ci` in backend): it starts/stops the Firestore emulator, installs on that emulator only and uses `am instrument`, so it never clears or uninstalls the app or touches a physical phone · Why: connectedDebugAndroidTest runs on every attached device with clearPackageData and uninstalls afterwards · Where: android/scripts/settle-rules-e2e.sh · Learned: without the Orchestrator all tests share one process, so connect to the emulator once (useEmulator() can only be called before first use)
