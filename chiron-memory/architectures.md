# architecture

How the system is put together — layers, boundaries, and how data flows.

## Monorepo of three independent native projects

What: ios/ (SwiftUI, iOS 17+), android/ (Kotlin + Compose, API 26+) and backend/ (Firestore rules, emulator, seed/reset) are built separately with no ../ references; shared docs live once in docs/, and the only shared thing is the contract docs/firestore-schema.md · Why: two people build the apps in parallel, and the PRD calls for native apps, not Kotlin Multiplatform · Where: docs/repo-structure.md

## Android app is layered ui → domain → data, wired by Koin

What: android/ is one :app module with packages ui/ (Route + stateless Screen + ViewModel per feature), domain/ (models, repository interfaces, use cases, LiveState; no Android or Firebase imports), data/ (Firestore, DataStore, ConnectivityManager implementations) and di/AppModules.kt (Koin firebaseModule, dataModule, domainModule, viewModelModule) · Why: lets ViewModels and use cases be unit-tested on the JVM with fakes, and keeps Firestore details in one place · Where: android/app/src/main/java/com/splitparty/android/di/AppModules.kt · Learned: FirebaseFirestore is only bound for FirebaseConnection.Ready; the root shows the Setup screen before anything resolves it

## iOS Home pushes the party and confirmation screens through one NavigationStack

What: HomeNavigationStack (MainTabView.swift) holds a [HomeDestination] path with .party(id:) and .confirmJoin(partyId:); every open replaces the path, so Back from a party always lands on Home. PartyHub (owned by LiveDataStore) is the session-wide party state: open parties, Home banner priority (Back to your party > Join > merchant card), the clock and the host's expiry writes; it sets partyToOpenOnLaunch once on the first server-confirmed snapshot, which reopens the party after relaunch · Why: banners, countdowns and expiry must flip together on every screen, and the work order wants a relaunch to land on the open party · Where: ios/SplitParty/UI/MainTabView.swift, ios/SplitParty/App/PartyHub.swift
