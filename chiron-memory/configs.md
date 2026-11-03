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
