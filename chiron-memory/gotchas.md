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
