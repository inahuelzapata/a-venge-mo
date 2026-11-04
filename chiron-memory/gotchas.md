# gotcha

A non-obvious pitfall or trap, learned the hard way.

## Firestore rules cannot loop over party members

What: firestore.rules validates members entries by indexing memberIds[0..7], so a party is capped at 8 members · Why: the rules language has no loops or map iteration · Where: backend/firestore.rules validMembers · Learned: per-element validation of variable-length lists in rules needs a fixed cap documented in the schema

## The iOS project uses a synchronized folder, so Info.plist sits outside it

What: ios/SplitParty is a PBXFileSystemSynchronizedRootGroup (any file dropped in is compiled or bundled), so Info.plist and the xcconfigs live in ios/Config, outside the synced folder · Why: an Info.plist inside a synced folder gets copied as a resource and the build fails with duplicate outputs · Where: ios/SplitParty.xcodeproj/project.pbxproj · Learned: GoogleService-Info.plist is placed in ios/SplitParty/ precisely so it is bundled without a project edit

## npm test / npm run verify fail on Node 22 because they pass a directory to node --test

What: backend/package.json runs `node --test test/unit/` and `test/emulator/`; Node 21+ treats those as files to run, not directories to scan, so they fail with "test failed" and no tests run; `node --test test/unit/*.test.js` works · Why: Node 20 scanned directories, Node 21+ takes glob patterns instead · Where: backend/package.json scripts · Learned: on Node 22, pass the test files or a glob, not a bare directory

## Firestore listeners never fail when offline

What: Snapshot listeners keep serving cache with metadata.isFromCache=true instead of erroring when the network or the emulator is gone; Android derives the error state from ConnectivityManager (offline at once) plus "no server snapshot for 5 s" (MetadataChanges.INCLUDE), and recovers on the next server snapshot · Why: the feed must show a visible error in airplane mode instead of stale or blank data · Where: android/app/src/main/java/com/splitparty/android/ui/LiveState.kt, data/NetworkMonitor.kt · Learned: in ConnectivityManager.onLost, report offline directly; activeNetwork can still return the lost network for a moment

## Android Gradle build fails on JDK 22+ with a bare version number

What: ./gradlew (Gradle 8.11.1) under Android Studio's bundled JBR 25 fails with only "What went wrong: 25.0.4"; it works with JDK 17-21, e.g. JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home · Why: Gradle 8.11 does not support running on Java 25 · Where: android/gradle/wrapper/gradle-wrapper.properties, android/README.md
