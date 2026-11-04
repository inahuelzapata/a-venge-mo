# architecture

How the system is put together — layers, boundaries, and how data flows.

## Monorepo of three independent native projects

What: ios/ (SwiftUI, iOS 17+), android/ (Kotlin + Compose, API 26+) and backend/ (Firestore rules, emulator, seed/reset) are built separately with no ../ references; shared docs live once in docs/, and the only shared thing is the contract docs/firestore-schema.md · Why: two people build the apps in parallel, and the PRD calls for native apps, not Kotlin Multiplatform · Where: docs/repo-structure.md

## Android app is layered ui → domain → data, wired by Koin

What: android/ is one :app module with packages ui/ (Route + stateless Screen + ViewModel per feature), domain/ (models, repository interfaces, use cases, LiveState; no Android or Firebase imports), data/ (Firestore, DataStore, ConnectivityManager implementations) and di/AppModules.kt (Koin firebaseModule, dataModule, domainModule, viewModelModule) · Why: lets ViewModels and use cases be unit-tested on the JVM with fakes, and keeps Firestore details in one place · Where: android/app/src/main/java/com/splitparty/android/di/AppModules.kt · Learned: FirebaseFirestore is only bound for FirebaseConnection.Ready; the root shows the Setup screen before anything resolves it
