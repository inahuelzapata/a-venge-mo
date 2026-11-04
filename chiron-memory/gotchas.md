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

## Headless iOS simulators always show the software keyboard

What: Without Simulator.app (not shipped with this Xcode), simctl offers no hardware-keyboard toggle, so a focused field always brings up the iOS keyboard, plus a "slide to type" tutorial card on first use; capture-ios.sh disables the card via `defaults write com.apple.keyboard.preferences DidShowContinuousPathIntroduction -bool true` · Why: the login field autofocuses, so login captures include the keyboard · Where: docs/design/capture/capture-ios.sh, android ParityScreenshotTest (329 dp keyboard stand-in)

