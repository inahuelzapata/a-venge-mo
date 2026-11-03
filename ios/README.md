# ios — Split Party iOS app

Native SwiftUI app, iOS 17+. Self-contained: the Xcode project references
nothing outside this folder. It implements the data contract in
[`docs/firestore-schema.md`](../docs/firestore-schema.md) (linked, never copied).

Today it shows a placeholder screen with the configured Firestore target. The
app-shell Work Order builds on this project.

## Layout

| Path | What |
| ---- | ---- |
| `SplitParty.xcodeproj` | The project. Open this. |
| `SplitParty/` | App sources and assets. This folder is **synchronized** with Xcode: files added here are part of the app with no project edit. |
| `Config/Base.xcconfig` | Shared build settings; optionally includes your local override |
| `Config/FirestoreTarget.example.xcconfig` | Template for your git-ignored `FirestoreTarget.local.xcconfig` |
| `Config/Info.plist` | Custom Info.plist keys (Firestore target, local-network prompt) |

## Run on a physical iPhone

Requires Xcode 16+ with the iOS platform installed (Xcode → Settings →
Components).

1. `cp Config/FirestoreTarget.example.xcconfig Config/FirestoreTarget.local.xcconfig`
2. In that file set `DEVELOPMENT_TEAM` (your Apple team id) and, with a free
   personal team, a unique `PRODUCT_BUNDLE_IDENTIFIER`. Set the Firestore
   target (below).
3. `open SplitParty.xcodeproj`, select your iPhone, press Run. On first launch
   accept the local-network prompt (needed to reach the emulator).

Without the local file the app still builds; the screen says the target is
not configured.

## Firestore target: the one place to switch

`Config/FirestoreTarget.local.xcconfig` (git-ignored):

```
FIREBASE_TARGET = emulator              // or hosted
FIREBASE_PROJECT_ID = demo-split-party  // or your hosted project id
FIRESTORE_EMULATOR_HOST = 192.168.1.20:8080   // laptop LAN IP; empty for hosted
```

Rebuild after changing it. Code reads it via `FirestoreTarget.fromBundle()`.
The keys and the demo setup are the same as Android and the backend: see
[Firestore target](../docs/firestore-target.md). The emulator itself is
started from [`backend/`](../backend); this app never runs its own.

## Firebase config file

`GoogleService-Info.plist` is **not committed**. Download it from the Firebase
console (iOS app with your bundle id) and put it at
`SplitParty/GoogleService-Info.plist`. Steps:
[`Config/GoogleService-Info.README.md`](Config/GoogleService-Info.README.md).
Until it is there, the placeholder screen shows a warning.
