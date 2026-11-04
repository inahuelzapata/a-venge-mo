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
