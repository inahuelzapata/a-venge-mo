# config

Setup and configuration — env vars, flags, how to run the project.

## Firestore target is chosen by FIREBASE_TARGET

What: The seed/reset scripts need FIREBASE_TARGET=emulator|hosted plus FIREBASE_PROJECT_ID; emulator also needs FIRESTORE_EMULATOR_HOST, hosted needs GOOGLE_APPLICATION_CREDENTIALS (a key outside the repo) and refuses to run if FIRESTORE_EMULATOR_HOST is set; reset on hosted needs --yes · Why: the work order forbids writing anywhere unless the target is explicit, and the Admin SDK silently honours FIRESTORE_EMULATOR_HOST · Where: backend/lib/env.js, backend/.env.example

## Emulator tests need Java and run serially

What: npm run verify (backend/) runs the Firestore emulator through emulators:exec, which needs a JDK 21 on PATH (e.g. /opt/homebrew/opt/openjdk@21/bin, or a Temurin 21 JAVA_HOME); emulator test files run with --test-concurrency=1, and the rules test uses its own project id (demo-split-party-rules, hence singleProjectMode false) · Why: the acceptance test asserts exact collection contents and would race with the rules test on shared data · Where: backend/package.json, backend/firebase.json

## iOS emulator target needs no GoogleService-Info.plist

What: For FIREBASE_TARGET=emulator the iOS app builds FirebaseOptions from placeholder app/sender ids plus FIREBASE_PROJECT_ID; only hosted reads GoogleService-Info.plist, and refuses it if its PROJECT_ID differs from FIREBASE_PROJECT_ID · Why: emulator demos should work on a fresh clone with no Firebase console step, and a mismatched plist would silently show another project's data · Where: ios/SplitParty/Data/FirebaseBootstrap.swift

## iOS Firebase dependency is SwiftPM with a committed Package.resolved

What: firebase-ios-sdk (FirebaseFirestore product only) is an XCRemoteSwiftPackageReference in the pbxproj; the pin lives in SplitParty.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved, which is committed · Why: every clone resolves the same SDK version; the root .gitignore's .swiftpm/ rule does not cover that path · Where: ios/SplitParty.xcodeproj/project.pbxproj

## Images are stored with Git LFS

What: .gitattributes routes ios/SplitPartyTests/__Snapshots__/** and docs/design/*.jpg|png through LFS; a clone needs git-lfs and `git lfs install` (else `git lfs pull`), otherwise the PNGs are pointer files and snapshot tests fail · Why: binary images would bloat the history · Where: .gitattributes, ios/README.md · Learned: Homebrew has no git-lfs bottle for Intel on new macOS and compiles Go and LLVM from source; the official GitHub release binary installs in seconds

## Snapshot references are recorded on the iOS 26.5 simulator

What: ScreenSnapshotTests skips on any other iOS version; re-record with TEST_RUNNER_SNAPSHOT_TESTING_RECORD=all on an iPhone simulator running 26.5; images are a fixed 390x844 @3x so the iPhone model does not matter · Why: text rendering changes between iOS versions · Where: ios/SplitPartyTests/ScreenSnapshotTests.swift, ios/README.md

## __Snapshots__ is excluded from the test bundle

What: SplitPartyTests is a synchronized folder, so a PBXFileSystemSynchronizedBuildFileExceptionSet excludes __Snapshots__ from the test target · Why: otherwise every reference PNG is copied into the test bundle as a resource · Where: ios/SplitParty.xcodeproj/project.pbxproj
## Android build runs on Java 25 via Gradle Daemon JVM criteria

What: android/ uses Gradle 9.8.2 + AGP 9.5 (built-in Kotlin, no org.jetbrains.kotlin.android plugin) + Kotlin 2.4.21; gradle/gradle-daemon-jvm.properties pins the daemon to Java 25 and the foojay resolver in settings.gradle.kts downloads it if missing, so Android Studio and ./gradlew need no Gradle JDK setting; app bytecode is Java 21 (compileOptions), compile/target SDK 37 · Why: Android Studio bundles Java 25, which Gradle 8.11 could not run on ("incompatible with the Gradle JVM version 25") · Where: android/gradle/gradle-daemon-jvm.properties, android/settings.gradle.kts, android/app/build.gradle.kts · Learned: with built-in Kotlin the Kotlin version follows the Compose compiler plugin version on the classpath (AGP alone pins 2.2.10); change the JVM with ./gradlew updateDaemonJvm --jvm-version=N
