# config

Setup and configuration — env vars, flags, how to run the project.

## Firestore target is chosen by FIREBASE_TARGET, hosted by default

What: All three templates default to FIREBASE_TARGET=hosted; the emulator is an opt-in block. Backend hosted credentials: GOOGLE_APPLICATION_CREDENTIALS=service-account.json (key at backend/service-account.json, git-ignored, relative paths resolve from backend/), or empty to use gcloud application-default credentials. readConfig exits 2 before any SDK call on: missing target/project id, the template placeholder id, a demo- id for hosted, FIRESTORE_EMULATOR_HOST set with hosted, a key that is missing/unreadable/not a service account/for another project, or no credentials at all. reset on hosted needs --yes · Why: the work order wants the hosted project as the real demo path and forbids writing anywhere unless the target is explicit; the Admin SDK silently honours FIRESTORE_EMULATOR_HOST · Where: backend/lib/env.js, backend/.env.example, docs/integration-setup-guide.md

## Backend test and deploy commands

What: `npm run deploy:firestore` deploys rules + indexes to FIREBASE_PROJECT_ID from .env (no .firebaserc; it is git-ignored). `npm run test:hosted -- --wipe-demo-db` = deploy, reset, wait for composite indexes READY, node --test test/acceptance/, reset in finally (also on failure/Ctrl-C); refuses without the flag. `npm run test:rules` = emulators:exec on project demo-split-party-rules, ignores .env, needs JDK 21 on PATH; the rules test refuses if FIREBASE_TARGET=hosted or no emulator host. `npm test` = unit tests · Why: the acceptance suite runs on the same database as the demo; @firebase/rules-unit-testing only works against the emulator · Where: backend/package.json, backend/scripts/test-hosted.js, backend/lib/deploy.js

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

## resetDemo deploy and emulator test

What: `npm run deploy:functions` deploys only functions:resetDemo to FIREBASE_PROJECT_ID (needs Blaze plan, Service Account User on the credential, and an org policy that allows allUsers invokers). `npm run test:functions` builds functions/shared then runs emulators:exec --only firestore,functions on demo-split-party-functions; firebase-functions is a backend/ dependency so one npm install in backend/ serves the Functions emulator · Why: the function is the only way a phone can restore the seeded state · Where: backend/package.json, backend/firebase.json, backend/README.md · Learned: emulators:exec exports FIRESTORE_EMULATOR_HOST but no Functions emulator address, so the test reads host:port from firebase.json

## One-shot Demo reset: android/scripts/demo-reset.sh and LiveDemoResetTest

What: `android/scripts/demo-reset.sh [--skip-deploy] [--skip-install]` runs `npm run deploy:reset-endpoint`, writes the URL to RESET_DEMO_URL in android/firestore.properties, installs the debug + test APKs on every connected device, then runs e2e/LiveDemoResetTest with `am instrument` on one device (ANDROID_SERIAL, default first); the test drives the real app (Me → Demo reset → Reset, signs in as RESET_USER/maxi if signed out) and is skipped unless `liveDemoReset=true` · Why: connectedDebugAndroidTest clears app data (orchestrator clearPackageData) and uninstalls afterwards, and must never reset the hosted demo · Where: android/scripts/demo-reset.sh, android/app/src/androidTest/.../e2e/LiveDemoResetTest.kt · Learned: a skipped (assumption) test still ends in "OK (1 test)", so the script fails on INSTRUMENTATION_STATUS_CODE: -4; `vercel link` appends a duplicate `.vercel` line to backend/reset-endpoint/.gitignore, which the script reverts; the reset re-seeds "Your payments" above the Demo section, so the Me list jumps up and the lazy section is uncomposed: the test waits by performScrollToNode to the done/error node

## The acceptance suite also runs against the Firestore emulator

What: `FIREBASE_TARGET=emulator FIREBASE_PROJECT_ID=demo-split-party-accept SPLIT_PARTY_ACCEPTANCE_RUNNER=1 npx firebase emulators:exec --only firestore --project demo-split-party-accept "node --test --test-concurrency=1 test/acceptance/"` (from backend/) runs seed, create/pay/reset and the "deployed rules" checks against the repo's rules on the emulator · Why: npm run test:hosted wipes the hosted demo DB and redeploys rules, which is not something to do just to check a contract change · Where: backend/test/acceptance/acceptance.test.js, backend/lib/env.js · Learned: the Android Gradle build needs ANDROID_HOME=~/Library/Android/sdk (or local.properties) in fresh worktrees
