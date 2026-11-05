# architecture

How the system is put together — layers, boundaries, and how data flows.

## Monorepo of three independent native projects

What: ios/ (SwiftUI, iOS 17+), android/ (Kotlin + Compose, API 26+) and backend/ (Firestore rules, emulator, seed/reset) are built separately with no ../ references; shared docs live once in docs/, and the only shared thing is the contract docs/firestore-schema.md · Why: two people build the apps in parallel, and the PRD calls for native apps, not Kotlin Multiplatform · Where: docs/repo-structure.md

## Android app is layered ui → domain → data, wired by Koin

What: android/ is one :app module with packages ui/ (Route + stateless Screen + ViewModel per feature), domain/ (models, repository interfaces, use cases, LiveState; no Android or Firebase imports), data/ (Firestore, DataStore, ConnectivityManager implementations) and di/AppModules.kt (Koin firebaseModule, dataModule, domainModule, viewModelModule) · Why: lets ViewModels and use cases be unit-tested on the JVM with fakes, and keeps Firestore details in one place · Where: android/app/src/main/java/com/splitparty/android/di/AppModules.kt · Learned: FirebaseFirestore is only bound for FirebaseConnection.Ready; the root shows the Setup screen before anything resolves it

## Home pushes screens through one NavigationStack with a single Split Party destination

What: MainTabView wraps Home in HomeNavigationStack (NavigationStack with a [HomeDestination] path, nav bar hidden on Home); the banner appends .splitParty and navigationDestination maps it to SplitPartyPlaceholderView. The bottom bar stays visible on pushed screens and Back keeps Home's scroll position because Home stays mounted · Why: the placeholder must be the one point a real party screen replaces, without touching the card · Where: ios/SplitParty/UI/MainTabView.swift, ios/SplitParty/UI/SplitPartyPlaceholderView.swift · Learned: snapshot tests open a pushed screen via MainTabView(initialHomePath:)

## Android total, tip and share live on the party screen, with entry screens drawn over it

What: PartyViewModel derives TotalUi (bill, tip, total, headcount, own share) from the single party listener with Party.split (a port of backend/lib/split.js in domain/split). Host writes (setBill, setTip, removeMember) and leave are Firestore transactions that re-read the party, refuse after expiry, and update only their own fields, so a concurrent join is retried on top and nothing is lost. The keypad, scan and confirmation screens are a TotalStep overlay inside PartyRoute, not nav routes, and BackHandler returns to the party with nothing written. Receipt OCR is CameraX in-memory capture plus bundled ML Kit (offline), and ReceiptTotalParser picks the total line. A member who disappears from memberIds (and isn't leaving locally) gets PartyEvent.Removed, and the shell pops to Home with "You were removed from X's party" · Why: the work order needs every phone to agree within a second, no half-applied writes, and back from entry screens never writing · Where: android ui/party/{PartyViewModel,TotalArea,TotalEntry,PartyDialogs}.kt, ui/scan/ReceiptScanScreen.kt, data/{FirestorePartyRepository,MlKitReceiptRecognizer}.kt, ui/shell/MainShell.kt
