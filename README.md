# aven-mo — Split Party

A Venmo-like PoC that demoes nearby payments: friends at a partner merchant
join a **Split Party** with a two-emoji code and settle the bill in one tap.
Two native apps (iOS and Android) share a single Firestore database.

**Start here:** [Integration & Setup Guide](docs/integration-setup-guide.md)
(fresh clone → phones on the hosted Firebase project → passing tests).

**Docs:** [PRD](docs/prd.md) · [Firestore schema (the data contract)](docs/firestore-schema.md) ·
[Demo runbook](docs/demo-runbook.md) · [Firestore target](docs/firestore-target.md) ·
[Repository structure](docs/repo-structure.md) ·
[Payments integration PRD](docs/payments-integration/prd-payments-integration.md)

## What's where

| Folder | Purpose |
| ------ | ------- |
| [`ios/`](ios) | Native SwiftUI iOS app (iOS 17+); opened, built and run from this folder only |
| [`android/`](android) | Native Kotlin + Jetpack Compose Android app (API 26+); opened, built and run from this folder only |
| [`backend/`](backend) | Firestore rules and indexes (deploy command), the seed/reset scripts, the tests, and the opt-in emulator config |
| [`docs/`](docs) | Shared docs: PRD, Firestore schema, demo runbook, conventions |
| [`chiron-memory/`](chiron-memory) | Project memory (decisions, conventions, gotchas) for AI-assisted sessions |

The three projects are independent: nothing is shared at build time, only
the [schema](docs/firestore-schema.md). New platform-specific work goes in
its platform's folder, and shared docs go in `docs/`. See
[Repository structure](docs/repo-structure.md).

## Quick start

Everything targets the **hosted** Firebase project by default (Cloud
Firestore), so phones work on any network, including mobile data. The local
emulator is an explicit opt-in for offline work. Each piece reads one git-ignored config file;
copy its template and set the project id. Full steps:
[Integration & Setup Guide](docs/integration-setup-guide.md).

### Backend: deploy, seed, test

```sh
cd backend
npm install
cp .env.example .env                     # set FIREBASE_PROJECT_ID
# save the service-account key as backend/service-account.json (or use gcloud ADC)
npm run deploy:firestore                 # rules + indexes to the hosted project
npm run deploy:functions                 # in-app Demo reset, Blaze projects (or, without Blaze:)
npm run deploy:reset-endpoint            # in-app Demo reset on free Vercel; set RESET_DEMO_URL (setup guide 3b)
npm run seed                             # 14 mutual friends, Tony's, seeded feed, no parties
npm run reset -- --yes                   # back to the seeded state
npm run test:hosted -- --wipe-demo-db    # acceptance suite on the demo database (resets after)
npm run test:rules                       # security-rules suite, local emulator only
```

Details: [`backend/README.md`](backend/README.md).

### Run the iOS app

```sh
cp ios/Config/FirestoreTarget.example.xcconfig ios/Config/FirestoreTarget.local.xcconfig   # team id + project id
cp ~/Downloads/GoogleService-Info.plist ios/SplitParty/
open ios/SplitParty.xcodeproj
```

Pick your iPhone and press Run. Details: [`ios/README.md`](ios/README.md).

### Run the Android app

```sh
cp android/firestore.properties.example android/firestore.properties   # project id
cp ~/Downloads/google-services.json android/app/
```

Open the `android/` folder in Android Studio, pick your phone and press Run
(or run `cd android && ./gradlew installDebug`). Details:
[`android/README.md`](android/README.md).

### Run the full demo

Phones and scripts must point at the **same** database: everywhere
`FIREBASE_TARGET=hosted` and the same `FIREBASE_PROJECT_ID`
([Firestore target](docs/firestore-target.md)). Then follow the
[demo runbook](docs/demo-runbook.md): seed, log in as `maxi`, `annie` and
`christophe` on three phones, run the party, reset.

To work offline instead, switch each config file to its emulator block and run
`npm run emulators` in `backend/`; see the guide.

## Secrets

Never commit `GoogleService-Info.plist`, `google-services.json`, `.env` files,
`backend/service-account.json` or any other service-account key; they are git-ignored
(the repo is public). Each folder's README says where its file goes, and
[Sharing the config files with colleagues](docs/integration-setup-guide.md#sharing-the-config-files-with-colleagues)
says how a teammate gets them. There is no CI: each piece is built and verified locally.
