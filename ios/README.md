# ios — Split Party iOS app

Native SwiftUI app, iOS 17+. Self-contained: the Xcode project references
nothing outside this folder. It implements the data contract in
[`docs/firestore-schema.md`](../docs/firestore-schema.md) (linked, never copied)
and matches the [app shell design](../docs/design/README.md).

What it does today (PoC-1 to PoC-6): username login (Maxi, Annie, Christophe,
any case), a live Home feed of everyone's payments, a Me tab with the live
mock balance and the user's own payments, and a bottom bar with Home,
Pay/Request (placeholder) and Me. The phone stays signed in until Log out.

## Layout

| Path | What |
| ---- | ---- |
| `SplitParty.xcodeproj` | The project. Open this. |
| `SplitParty/` | App sources and assets. This folder is **synchronized** with Xcode: files added here are part of the app with no project edit. |
| `SplitParty/Data/` | Firestore access (`PaymentRepository`), models, money formatting, session storage |
| `SplitParty/App/` | `AppModel` (target + who is signed in) and `LiveDataStore` (live listeners, error state) |
| `SplitParty/UI/` | Login, Home, Me, bottom bar, theme |
| `SplitPartyTests/` | Unit tests (money, usernames, document mapping). Run with ⌘U. |
| `Config/Base.xcconfig` | Shared build settings; optionally includes your local override |
| `Config/FirestoreTarget.example.xcconfig` | Template for your git-ignored `FirestoreTarget.local.xcconfig` |
| `Config/Info.plist` | Custom Info.plist keys (Firestore target, local-network prompt) |

Dependency: [firebase-ios-sdk](https://github.com/firebase/firebase-ios-sdk)
(FirebaseFirestore only) via Swift Package Manager. Xcode fetches it on first
open; the pinned version is in `SplitParty.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`.

## Run on a physical iPhone

Requires Xcode 16+ **with the iOS platform installed** (Xcode → Settings →
Components; without it Xcode lists no run destinations), an iPhone on iOS 17+
with Developer Mode on, and an Apple ID added in Xcode → Settings → Accounts.

1. Start a database and seed it (see [demo runbook](../docs/demo-runbook.md)):
   for the emulator, `cd backend && npm run emulators`, then `npm run seed` in
   another terminal.
2. `cp Config/FirestoreTarget.example.xcconfig Config/FirestoreTarget.local.xcconfig`
3. In that file set `DEVELOPMENT_TEAM` (your Apple team id, shown in Xcode →
   Settings → Accounts) and, with a free personal team, a unique
   `PRODUCT_BUNDLE_IDENTIFIER`. Set the Firestore target (below).
4. `open SplitParty.xcodeproj`, wait for the package to resolve, select your
   iPhone, press Run. On first launch accept the local-network prompt (needed
   to reach the emulator). The login screen footer shows which database the
   app is using.
5. Sign in as `maxi`, `annie` or `christophe`.

Without the local file the app still builds; the login screen says the target
is not configured and sign-in stays disabled.

## Firestore target: the one place to switch

`Config/FirestoreTarget.local.xcconfig` (git-ignored):

```
FIREBASE_TARGET = emulator              // or hosted
FIREBASE_PROJECT_ID = demo-split-party  // or your hosted project id
FIRESTORE_EMULATOR_HOST = 192.168.1.20:8080   // laptop LAN IP; empty for hosted
```

Rebuild after changing it; no code change is needed to switch. Get the laptop
IP with `ipconfig getifaddr en0`; the phone and laptop must be on the same
Wi-Fi. The keys and the demo setup are the same as Android and the backend:
see [Firestore target](../docs/firestore-target.md). The emulator itself is
started from [`backend/`](../backend); this app never runs its own.

## Firebase config file (hosted only)

- **Emulator:** no config file needed. The app configures Firebase from the
  project id alone.
- **Hosted:** `GoogleService-Info.plist` is required and **not committed**.
  Download it from the Firebase console (iOS app with your bundle id, in the
  same project as `FIREBASE_PROJECT_ID`) and put it at
  `SplitParty/GoogleService-Info.plist`. Steps:
  [`Config/GoogleService-Info.README.md`](Config/GoogleService-Info.README.md).
  If it is missing or from another project, the login screen says so.

## Behaviour worth knowing

- **Money** is read as integer cents and only formatted for display
  (`Money.format`, via `Decimal`). A non-integer amount in the database is
  ignored, not rounded.
- **Feed and Me are live** listeners on the documented queries. A new row
  appears at the top with a brief highlight.
- **No offline cache.** If the database can't be reached (airplane mode,
  emulator stopped), Home and Me show an error card within about 4 seconds
  instead of stale rows, and recover by themselves when it comes back.
- Names are shown as display names, never "You", so every phone shows the
  identical feed.

## Troubleshooting

| Symptom | Fix |
| ------- | --- |
| No iPhone in the run destinations | Install the iOS platform in Xcode → Settings → Components |
| "Can't reach the database" on a phone | Emulator running? `FIRESTORE_EMULATOR_HOST` is the laptop's LAN IP, not 127.0.0.1? Local-network permission allowed (Settings → Privacy → Local Network)? |
| Feed empty but seeded | Phone and seed script use different `FIREBASE_PROJECT_ID`s |
| Signing error | Set `DEVELOPMENT_TEAM` and a unique `PRODUCT_BUNDLE_IDENTIFIER` in the local xcconfig |
