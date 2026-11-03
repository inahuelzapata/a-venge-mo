# aven-mo — Split Party

A Venmo-like PoC that demoes nearby payments: friends at a partner merchant
join a **Split Party** with a three-emoji code and settle the bill in one tap.
Two native apps (iOS and Android) share a single Firestore database.

**Docs:** [PRD](docs/prd.md) · [Firestore schema (the data contract)](docs/firestore-schema.md) ·
[Demo runbook](docs/demo-runbook.md) · [Firestore target](docs/firestore-target.md) ·
[Repository structure](docs/repo-structure.md)

## What's where

| Folder | Purpose |
| ------ | ------- |
| [`ios/`](ios) | Native SwiftUI iOS app (iOS 17+); opened, built and run from this folder only |
| [`android/`](android) | Native Kotlin + Jetpack Compose Android app (API 26+); opened, built and run from this folder only |
| [`backend/`](backend) | Firestore rules and indexes, the emulator config, and the seed/reset scripts with their tests |
| [`docs/`](docs) | Shared docs: PRD, Firestore schema, demo runbook, conventions |
| [`chiron-memory/`](chiron-memory) | Project memory (decisions, conventions, gotchas) for AI-assisted sessions |

The three projects are independent: nothing is shared at build time, only
the [schema](docs/firestore-schema.md). New platform-specific work goes in
its platform's folder, and shared docs go in `docs/`. See
[Repository structure](docs/repo-structure.md).

## Quick start

### Run the iOS app

```sh
cp ios/Config/FirestoreTarget.example.xcconfig ios/Config/FirestoreTarget.local.xcconfig   # set your team id + target
open ios/SplitParty.xcodeproj
```

Pick your iPhone and press Run. Details: [`ios/README.md`](ios/README.md).

### Run the Android app

```sh
cp android/firestore.properties.example android/firestore.properties   # set the target
```

Open the `android/` folder in Android Studio, pick your phone and press Run
(or run `cd android && ./gradlew installDebug`). Details:
[`android/README.md`](android/README.md).

### Seed and reset the database

```sh
cd backend
npm install
cp .env.example .env       # emulator by default; see the file for hosted
npm run emulators          # emulator only, in its own terminal (needs Java 21+)
npm run seed               # 3 friends, Tony's, seeded feed, no parties
npm run reset              # back to the seeded state (hosted: npm run reset -- --yes)
```

Details: [`backend/README.md`](backend/README.md).

### Run the full demo

Phones and scripts must point at the **same** database. Use the same three
keys everywhere ([Firestore target](docs/firestore-target.md)):

- **Emulator:** `backend/.env` uses `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`;
  each phone uses `FIRESTORE_EMULATOR_HOST=<laptop LAN IP>:8080`. All use
  `FIREBASE_TARGET=emulator` and `FIREBASE_PROJECT_ID=demo-split-party`.
- **Hosted:** everywhere `FIREBASE_TARGET=hosted` and the same
  `FIREBASE_PROJECT_ID`, with no emulator host.

Then follow the [demo runbook](docs/demo-runbook.md): seed, log in as
`maxi`, `annie` and `christophe` on three phones, run the party, reset.

## Secrets

Never commit `GoogleService-Info.plist`, `google-services.json`, `.env` files
or service-account keys; they are git-ignored. Each folder's README says
where its file goes. There is no CI: each piece is built and verified locally.
