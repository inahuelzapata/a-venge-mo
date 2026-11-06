# gotcha

A non-obvious pitfall or trap, learned the hard way.

## Firestore rules cannot loop over party members

What: firestore.rules validates members entries by indexing memberIds[0..7], so a party is capped at 8 members · Why: the rules language has no loops or map iteration · Where: backend/firestore.rules validMembers · Learned: per-element validation of variable-length lists in rules needs a fixed cap documented in the schema

## The iOS project uses a synchronized folder, so Info.plist sits outside it

What: ios/SplitParty is a PBXFileSystemSynchronizedRootGroup (any file dropped in is compiled or bundled), so Info.plist and the xcconfigs live in ios/Config, outside the synced folder · Why: an Info.plist inside a synced folder gets copied as a resource and the build fails with duplicate outputs · Where: ios/SplitParty.xcodeproj/project.pbxproj · Learned: GoogleService-Info.plist is placed in ios/SplitParty/ precisely so it is bundled without a project edit

## npm test / npm run verify fail on Node 22 because they pass a directory to node --test

What: backend/package.json runs `node --test test/unit/` and `test/emulator/`; Node 21+ treats those as files to run, not directories to scan, so they fail with "test failed" and no tests run; `node --test test/unit/*.test.js` works · Why: Node 20 scanned directories, Node 21+ takes glob patterns instead · Where: backend/package.json scripts · Learned: on Node 22, pass the test files or a glob, not a bare directory

## Xcode without the iOS platform lists no destinations at all

What: With Xcode 26.5 but no iOS 26.5 platform component, xcodebuild fails with "Found no destinations" even for old simulators; the app can still be type-checked and run in a simulator by copying the sources into a scratch SwiftPM package and building with --triple <arch>-apple-ios17.0-simulator --sdk $(xcrun --sdk iphonesimulator --show-sdk-path) · Why: Xcode validates destinations against the installed platform, while SwiftPM only needs the SDK · Where: ios/SplitParty.xcodeproj · Learned: on Intel Macs the simulator needs x86_64, not arm64 ("Failed to find matching arch" on install)

## Swift 5 mode rejects bare regex literals

What: The iOS target uses SWIFT_VERSION 5.0, so /…/ regex literals fail to parse; use the #/…/# form · Why: bare-slash regex needs Swift 6 mode or -enable-bare-slash-regex · Where: ios/SplitParty/Data/Username.swift

## Snapshot references must be sRGB, not the simulator's Display P3

What: ScreenSnapshotTests renders with displayGamut .SRGB; with P3 PNGs swift-snapshot-testing's perceptual compare reported the saturated brand blue as different, so any tiny unrelated difference (the animated spinner) failed the whole screen in 3 of 4 runs · Why: the byte-exact fast path hid the problem until an image differed at all · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift · Learned: when a snapshot flakes, diff the actual pixels yourself; the library's failing-pixel count included areas that were byte-identical

## Snapshots must be drawn through the key window

What: ScreenSnapshotTests uses .image(drawHierarchyInKeyWindow: true, …); plain layer rendering drew the bottom bar's material see-through and left hairlines at capsule edges · Why: CALayer rendering skips UIVisualEffectView blur · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift

## Snapshot pixel allowance hides colour changes

What: Snapshots use precision 1.0 with perceptualPrecision 0.98 (0.995 only for the spinner screen); with precision 0.99 a shifted brand blue still passed on screens where blue covers under 1 % · Why: precision counts how many pixels may differ, not by how much · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift · Learned: verify a snapshot setup by making a small deliberate change and checking every screen fails
## Firestore listeners never fail when offline

What: Snapshot listeners keep serving cache with metadata.isFromCache=true instead of erroring when the network or the emulator is gone; Android derives the error state from ConnectivityManager (offline at once) plus "no server snapshot for 5 s" (MetadataChanges.INCLUDE), and recovers on the next server snapshot · Why: the feed must show a visible error in airplane mode instead of stale or blank data · Where: android/app/src/main/java/com/splitparty/android/domain/live/LiveState.kt, data/AndroidConnectivityObserver.kt · Learned: in ConnectivityManager.onLost, report offline directly; activeNetwork can still return the lost network for a moment

## Android 17 (targetSdk 37) blocks LAN traffic until ACCESS_LOCAL_NETWORK is granted

What: With targetSdk 37, Firestore connections to the laptop emulator (LAN IP or 10.0.2.2) silently time out ("Could not reach Cloud Firestore backend"); the app declares ACCESS_LOCAL_NETWORK and LocalNetworkGate requests it before login on API 37+ when FIREBASE_TARGET=emulator (hosted needs nothing) · Why: Android 17 enables local network protection by default for apps targeting SDK 37 · Where: android/app/src/main/java/com/splitparty/android/ui/gate/LocalNetworkGate.kt, AndroidManifest.xml · Learned: adb shell/nc tests from the device still reach the host, so only the app is blocked; the system prompt reads "nearby devices"; Compose supportingText (the login error) does not appear as its own node in uiautomator dumps

## Stopping the Firestore emulator by port also cuts the Android AVD off from adb

What: `lsof -ti tcp:8080 | xargs kill` also matches clients connected to 8080 (the AVD forwards 10.0.2.2:8080 through its qemu process); in practice adb lost the AVD until `adb kill-server && adb start-server` · Why: lsof without -sTCP:LISTEN matches both ends of each connection · Where: backend `npm run emulators` (port 8080) · Learned: to simulate an unreachable database, kill only the listener (`lsof -ti tcp:8080 -sTCP:LISTEN`) or stop the emulators task

## detekt 1.23 cannot run on the Java 25 Gradle daemon; use detekt 2.0 alpha

What: detekt 1.23.8 (last stable) fails first with "Invalid value (25) passed to --jvm-target" and, even with jvmTarget pinned to 21, with a bare "25.0.4" (its embedded Kotlin 2.0 can't parse the JDK version); android/ uses detekt 2.0.0-alpha.6 (plugin id dev.detekt), the version io.nlopez.compose.rules 0.6.8 is built against · Why: Gradle runs on Java 25 (gradle-daemon-jvm.properties) · Where: android/gradle/libs.versions.toml, android/config/detekt/detekt.yml · Learned: detekt 2 renamed config keys (LongParameterList functionThreshold → allowedFunctionParameters, UnusedPrivateMember split into UnusedPrivateFunction/Property); move to a stable 2.x when released

## ViewModels in tests must be created after MainDispatcherRule runs

What: Android ViewModel tests build the ViewModel `by lazy` (or in the test body), never as a plain field · Why: JUnit initialises fields before rules run, so a field-built ViewModel starts viewModelScope (Eagerly/stateIn) on the real Dispatchers.Main and its flows never advance; with a StateFlow-backed fake, values set before first access are also conflated into one emission (a "new row" looks like the first load) · Where: android/app/src/test/java/com/splitparty/android/testing/MainDispatcherRule.kt, ui/*/…ViewModelTest.kt · Learned: touch `viewModel.state` before pushing the first fake snapshot when the test is about changes between snapshots

## Robolectric Compose tests need JDK flags on Java 25 and espresso 3.7 on API 37

What: Robolectric 4.17 tests failed with "Failed to interact with raw FileDescriptor internals" until the test JVM got `--add-opens=java.base/java.io` and `--add-exports=java.base/jdk.internal.access` (ALL-UNNAMED), then with NoSuchMethodException InputManager.getInstance() until espresso-core was pinned to 3.7.0 (Compose ui-test pulls 3.5.0) · Why: unit tests run on the Gradle JVM (Java 25), and API 37 removed InputManager.getInstance · Where: android/app/build.gradle.kts testOptions.unitTests.all, android/gradle/libs.versions.toml · Learned: the real SplitPartyApplication would startKoin once per Robolectric test, so app/src/test/resources/robolectric.properties uses a plain android.app.Application and tests start Koin themselves

## Paparazzi and Robolectric run together in android/app unit tests

What: Paparazzi 2.0.0-alpha05.1 and Robolectric 4.17 tests share the :app testDebugUnitTest task without conflicts (69 tests pass together), although older Paparazzi docs advise keeping them in separate modules · Why: avoids a separate screenshot module, which could not depend on the application module's screens anyway · Where: android/app/build.gradle.kts, app/src/test/resources/robolectric.properties · Learned: if they start clashing after an upgrade, check this first; the fallback is moving screens into an Android library module

## iOS snapshots shift slightly after the simulator restarts or captures run

What: After capture-ios.sh (status bar override) and a simulator shutdown/reboot, ScreenSnapshotTests failed on Home and Me with every pixel off by at most 3/255 and no layout change; re-recording on the current simulator state made two full runs pass · Why: the screens that failed are the ones with content blurred under the material tab bar, whose rendering moved with the fresh simulator state · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift, docs/design/capture/capture-ios.sh · Learned: record snapshots and run them on the same booted simulator session; measure the diff before assuming a UI regression

## iOS reference captures need the simulator's hardware keyboard disconnected

What: With I/O ▸ Keyboard ▸ Connect Hardware Keyboard on (com.apple.iphonesimulator ConnectHardwareKeyboard), the login captures come out without the software keyboard, unlike the reference set and Android's keyboard stand-in · Why: the focused field only shows the on-screen keyboard when no hardware keyboard is attached · Where: docs/design/capture/capture-ios.sh, docs/design/ios-captures/README.md
## Hosted deploy needs extra IAM roles on the Admin SDK service account

What: With only the default roles, `firebase deploy --only firestore` with the firebase-adminsdk key fails with 403 on serviceusage (API-enabled check) and then on firebaserules.googleapis.com:test; granting "Firebase Admin" + "Service Usage Consumer" in Cloud IAM fixes it after 1-2 minutes of propagation · Why: the Admin SDK service agent role covers Firestore data, not deploys · Where: backend/lib/deploy.js (prints the hint), docs/integration-setup-guide.md step 1 · Learned: seed/reset work with the default roles; a fresh production-mode database denies all client reads until the repo rules are deployed, so phones show "Can't load payments" (PERMISSION_DENIED)

## Probing a composite index with a query is unreliable

What: While the transactions(participantIds, createdAt desc) index was still building, the Me query with limit(1) succeeded but the same query without a limit failed FAILED_PRECONDITION; a query on an empty collection also succeeds. test-hosted therefore lists indexes through the Firestore admin REST API (collectionGroups/-/indexes) and waits for state READY · Where: backend/scripts/test-hosted.js waitForIndexes


## Navigating to Home with restoreState brings back a screen pushed over Home

What: switchTab reaches Home with popBackStack<HomeDestination>(saveState only when leaving a tab), never navigate(Home){ popUpTo(start){saveState}; restoreState } · Why: a non-inclusive popUpTo with saveState also maps the saved stack to the popUpTo destination (Home), so restoring Home re-pushes Split Party; the Home tab tap from the placeholder then did nothing · Where: android ui/shell/MainShell.kt switchTab, MainShellNavigationTest.homeTabFromThePlaceholderReturnsHome
## capture-ios.sh re-takes every capture, even screens a change did not touch

What: Running docs/design/capture/capture-ios.sh rewrites all PNGs in docs/design/ios-captures, and login/Me/Pay-Request come out byte-different from the committed ones (keyboard, cursor, status-bar noise) even when nothing changed · Why: committing those makes unrelated screens look changed to Android reviewers · Where: docs/design/capture/capture-ios.sh · Learned: after a run, git checkout the captures of screens the work did not change

## Infinite ViewModel timers hang runTest

What: A while(true){ delay() } ticker in viewModelScope (Dispatchers.Main = the rule's test dispatcher, sharing runTest's scheduler) makes runTest advance virtual time forever at the end of the test; the Gradle test task just never finishes. Timers must end (Clock.at deadlines, Clock.countdown until expiry), and fakes must behave like the real write (FakePartyRepository.expire stores status expired) or the host's expiry retry loop never stops · Where: android domain/Clock.kt, app/src/test/.../testing/Fakes.kt

## Compose testTag after clearAndSetSemantics is lost

What: In a modifier chain, a testTag placed after clearAndSetSemantics is cleared with the rest, so onNodeWithTag finds nothing; put testTag before clearAndSetSemantics · Where: android ui/home/PartyBanners.kt, ui/party/PartyScreen.kt

## A tap-swallowing clickable on an overlay merges its children for TalkBack

What: Adding `.clickable(indication = null) {}` to a full-screen overlay to keep taps from reaching the screen below merges every child into one semantics node, so TalkBack reads the whole screen as one button and tests can't find tagged children. Use `.pointerInput(Unit) { detectTapGestures { } }` instead, which adds no semantics · Where: android ui/party/TotalEntry.kt TotalEntryOverlay

## Robolectric's default screen is too short for the keypad screens

What: With no qualifiers, Robolectric lays out on a 320×470 dp screen, which leaves the keypad screen's amount and message area about 0 dp tall, so "is displayed" checks fail. Entry-flow tests use `@Config(qualifiers = "w393dp-h851dp")` (Pixel 5, as Paparazzi) · Where: android test ui/party/PartyRouteTest.kt, ConfirmScanTest.kt

## Android on-device tests: permission dialog, process kills and CameraX threads

What: In the on-device tests on the API 37 Play Store emulator, UiAutomator can't see the system camera-permission dialog (it lives in com.google.android.permissioncontroller, and a dump shows only systemui plus the paused app). Revoking a runtime permission kills the app and the test process with it. So "denied in settings" is set with `pm set-permission-flags <pkg> android.permission.CAMERA user-set user-fixed`, which makes the request answer denied at once, and the Orchestrator's `clearPackageData` gives every test a fresh, ungranted start. CameraX's PreviewView.getSurfaceProvider and bindToLifecycle must run on the main thread, but a LaunchedEffect can resume on another thread after `ProcessCameraProvider.awaitInstance`, so binding is wrapped in `withContext(Dispatchers.Main)`. Only the device tests caught this; Robolectric never binds a camera · Where: android app/src/androidTest/.../e2e/ReceiptScanE2ETest.kt, ui/scan/ReceiptScanScreen.kt bindCamera, app/build.gradle.kts testOptions
## Firestore transaction blocks on iOS must not inherit the main actor

What: The party transactions live in PartyWriter, a nonisolated struct, instead of the @MainActor PaymentRepository · Why: Firestore calls the transaction block on its own queue; a closure written inside a main-actor method inherits main-actor isolation and is then called off the main thread · Where: ios/SplitParty/Data/PartyRepository.swift · Learned: keep any SDK callback that runs on a background queue out of @MainActor types

## SwiftUI accessibilityIdentifier on a plain stack overwrites its children's identifiers

What: An .accessibilityIdentifier applied to a VStack that is not an accessibility element is copied onto every child, so the "Back to Home" button lost its own id; UI tests now find the expired state by its text · Why: SwiftUI propagates the modifier when the view is not a single element · Where: ios/SplitParty/UI/PartyView.swift (PartyClosedView), ios/SplitPartyUITests/PartyFlowUITests.swift · Learned: put identifiers on leaf views, or make the container .accessibilityElement(children: .contain) first

## iOS Home and Me snapshots fail on 2026-11-05 on the unmodified base commit

What: ScreenSnapshotTests testHomeFeed, testHomeEmpty and testMe fail by one pixel (match 0.9999997, perceptual precision 0.977 < 0.98) on the iPhone 17 / iOS 26.5 simulator, both on the total-slice branch and on its untouched base commit 3958861; they passed there the day before · Why: unknown; not caused by party code (verified on a clean worktree of the base). Likely date- or simulator-dependent rendering · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift · Learned: before blaming a branch for these failures, rerun them on a clean worktree of the base commit

## XCUITest launch argument values are parsed as property lists

What: A "-key value" launch argument whose value has spaces or an apostrophe (a receipt like "TONY'S|Greenwich Ave") arrives mangled ("TONYS", "Greenwich\ Ave") and can swallow the following arguments. Free text for the Debug app goes in `app.launchEnvironment` instead; the receipt scan reads `UITestReceipt` from the environment · Where: ios/SplitParty/UI/ReceiptScanView.swift ReceiptScanTestLaunch, ios/SplitPartyUITests/TotalFlowUITests.swift applyReceiptEnvironment · Learned: a test passed only because the value was the last argument; keep "-key value" for simple tokens

## The iOS floating tab bar does not inset pushed screens

What: The bottom bar is a safeAreaInset on MainTabView's ZStack, but scroll views on screens pushed in HomeNavigationStack still lay out to the screen bottom, so their last controls can sit under the bar with nothing to scroll. The party screen adds `.contentMargins(.bottom, Theme.floatingBarClearance /* 96 */, for: .scrollContent)` · Where: ios/SplitParty/UI/PartyView.swift, Theme.swift · Learned: any new pushed screen with controls near its end needs the same margin
## Compose testTag placed after clearAndSetSemantics is invisible to tests

What: `Modifier.clearAndSetSemantics { … }.testTag(x)` hides the tag (the later modifier is a cleared descendant); put `.testTag(x)` before clearAndSetSemantics. Likewise Text under clearAndSetSemantics can't be found by onNodeWithText; assert the contentDescription instead (the Me balance is now "Balance, 250 dollars") · Where: android ui/party/Settlement.kt, ui/shell MainShellNavigationTest

## Android LoginScreenTest.showsTheInlineErrorAndTarget already fails on main

What: It expects "Emulator test" on screen and fails on a clean checkout of main (ddf9907), so `./gradlew check` stops at testDebugUnitTest before verifyPaparazziDebug; run `./gradlew :app:verifyPaparazziDebug --tests '*ScreenshotTest*'` to verify goldens meanwhile. Re-recording also rewrites untouched Home goldens with sub-threshold noise; restore those with git checkout · Where: android app/src/test/.../ui/login/LoginScreenTest.kt:58

## A Pay commit that loses to another Pay gets PERMISSION_DENIED, not a retry

What: Two settle transactions on the same party racing: the loser's commit is rejected by the rules (`paid` is terminal for the party update, and its transactions/{partyId}_{payer} writes would be updates) with PERMISSION_DENIED instead of ABORTED, so the Firestore SDK does not retry it and the client sees a failure although the party was settled once. FirestorePartyRepository.settle therefore re-reads the party from the server after any failure and returns Done when it is paid · Why: the requirement is that Pay never fails because the party is already paid · Where: android data/FirestorePartyRepository.kt (settle, paidOnServer) · Learned: found by SettleRulesE2ETest against the emulator; iOS should handle its Pay failure the same way

## Android Demo reset failing with "check your connection" meant resetDemo was not deployed

What: The Android Demo reset showed the generic connection error because the hosted project had no `resetDemo` (the callable URL https://us-central1-<project>.cloudfunctions.net/resetDemo answered 404); the client's name, region and project were correct. A plain GET on that URL is a safe probe: 404 = not deployed, 400 = deployed (a GET never runs the reset) · Why: merging the function does not deploy it; `npm run deploy:functions` is a separate manual step per project, and the app collapsed NOT_FOUND into the offline message · Where: backend/scripts/deploy-functions.js, docs/integration-setup-guide.md, android data/FirebaseDemoResetRepository.kt · Learned: probe the endpoint before debugging the client; the app now names NOT_FOUND as "not deployed" and logs the Functions code

## Koin singleOf() injects defaulted constructor parameters too

What: `singleOf(::FirebaseDemoResetRepository)` crashed the Me tab at runtime with `NoDefinitionFoundException: java.lang.Long` once the class gained `timeoutMs: Long = TIMEOUT_MS`; `singleOf`/`viewModelOf` resolve every constructor parameter and ignore Kotlin defaults, and KoinModulesTest's `verify()` did not flag it · Why: constructor references carry no default values · Where: android di/AppModules.kt · Learned: give a class with test-only defaulted parameters an explicit `single<I> { Impl(get()) }`, and open the screen on a device after a DI change

## iOS UI tests: Home and hittability lie under pushed screens and the tab bar

What: `homeScreen` stays in the accessibility tree while a party screen is pushed over it, so waiting for it to exist proves nothing; wait for `partyScreen` to stop existing instead. An element behind the translucent floating tab bar still reports `isHittable == true`, so "swipe until hittable" leaves it half hidden in a capture; swipe a fixed number of times. System confirmation dialog buttons match twice: use `.firstMatch` · Where: ios/SplitPartyUITests/PayFlowUITests.swift, ReferenceCaptureUITests.swift (test15DemoReset)

## iOS expiry and 3-second UI tests fail when another simulator runs at the same time

What: PartyFlowUITests' expiry tests (parties built to expire ~10 s after launch) and the receipt scan's 3 s bar failed in a full run while a second simulator ran tests, and passed on an idle machine · Learned: run the full iOS UI suite with nothing else on the simulators before calling a failure real

## iOS: animating the party snapshot crossfades the open → paid swap and hides the first paid tick

What: PartyRoomModel applied every party snapshot inside Motion.animate(spring 0.45/0.7), so when the party turned `paid` SwiftUI crossfaded OpenPartyContent into PaidPartyContent for ~0.45 s; the paid screen's 100 ms + 280 ms tick sequence ran under the fade and members saw both badges tick in the same frame. The snapshot that turns a party paid is now applied without animation · Why: the settlement plays its own motion from its first frame; any container transition on top of it eats the sequence · Where: ios/SplitParty/UI/PartyView.swift (PartyRoomModel.listen), SettlementViews.swift (PaidPartyContent.play) · Learned: only visible in a frame-by-frame recording on a member phone; the host looked fine because it shows pending badges first

## Android unit tests crash in one JVM on this Mac; run the Paparazzi tests and the rest separately

What: `./gradlew :app:testDebugUnitTest` (and so `check`) dies a few seconds in with `java.lang.NoSuchMethodError: android.view.Surface.nativeSetProducerThrottlingEnabled(long, boolean)` from layoutlib when Paparazzi and Robolectric tests share the test JVM — on untouched HEAD too, so the task fails without listing failing tests. Run `:app:verifyPaparazziDebug --tests 'com.splitparty.android.screenshots.*'` and `:app:testDebugUnitTest --tests 'com.splitparty.android.ui.*' --tests '...domain.*' --tests '...data.*' --tests '...di.*' --tests '...format.*'` instead, then count from the result XMLs · Where: android/app/src/test · Learned: seen 2026-11-05 with Android SDK 37 on the AVD host; `echo $?` after a redirected gradle run in a pipeline reports the echo, not Gradle — read the log for BUILD FAILED

## A seed change is not live in the Demo reset until the reset backend is redeployed

What: The hosted project a-venge-mo runs the Demo reset on the Vercel endpoint (Cloud Function GET → 404, Vercel GET → 400), and that endpoint bundles its own copy of lib/seedData.js at deploy time. After any seed change, run `npm run seed` and `npm run deploy:reset-endpoint` (or `deploy:functions` on a Blaze project), or the first in-app Demo reset restores the old values · Why: the deployed reset doesn't read the repo · Where: backend/scripts/deploy-reset-endpoint.js, docs/integration-setup-guide.md 3b · Learned: check a deploy without resetting anything by sending a GET to both addresses. `vercel link` adds a duplicate `.vercel` line to backend/reset-endpoint/.gitignore on every deploy; revert it with git checkout

## Paparazzi aborts the JVM when it shares a test run with the Robolectric tests

What: `./gradlew testDebugUnitTest` (and plain record/verifyPaparazziDebug) can die with SIGABRT (exit 134, `NoSuchMethodError ... Surface.nativeSetProducerThrottlingEnabled`) once Paparazzi runs in the same test JVM as the Robolectric tests; run them split: `--tests 'com.splitparty.android.screenshots.*'` for record/verify, and the other packages (domain, ui, data, di, format) separately · Why: layoutlib's native Surface clashes with Robolectric's shadows in one JVM · Where: android/app (Paparazzi 2.0 alpha) · Learned: re-recorded Home goldens also change with the wall clock ("3 days ago" → "4 days ago"), so restore goldens a change did not touch; LoginScreenTest.showsTheInlineErrorAndTarget ("Emulator test" label not displayed) also fails on a clean checkout of main 16bc0d6, so it is not caused by join-code work (cause not investigated)

## Android lint has two CompositionLocal allowlists

What: A new CompositionLocal must be added to both `compose_allowed_composition_locals` in android/.editorconfig (ktlint compose rules) and `CompositionLocalAllowlist.allowedCompositionLocals` in android/config/detekt/detekt.yml. Also, Koin's verify() in KoinModulesTest flags constructor parameters such as File or defaulted interfaces on hand-built singles, so list them in extraTypes · Where: android/.editorconfig, android/config/detekt/detekt.yml, android/app/src/test/.../di/KoinModulesTest.kt · Learned: hit while adding LocalAvatarImages and AvatarImageStore

## iOS ScreenSnapshotTests all fail on unmodified main (2026-11-05)

What: All 8 ScreenSnapshotTests (login, home, Me, pay placeholder) fail with "Snapshot does not match reference" on the iOS 26.5 iPhone Air simulator, including on a pristine copy of main, while the rest of SplitPartyTests pass · Why: the reference PNGs are out of date with main or with the simulator/OS build, not caused by feature changes · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift, ios/SplitPartyTests/__Snapshots__/ · Learned: before blaming a change for snapshot failures, run the same tests on a clean `git archive HEAD` copy; re-recording the references is separate work

