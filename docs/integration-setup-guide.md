# Integration & Setup Guide

From a fresh clone to three phones on the **hosted** Firebase project, plus a
passing hosted acceptance run. The hosted project is the default everywhere.
The local emulator is an opt-in for offline work ([section 8](#8-opt-in-work-offline-against-the-emulator)).

The database is **Cloud Firestore**, not Realtime Database. The three keys that
pick the database are the same in every piece of the repo; see
[Firestore target](firestore-target.md).

## What you need

| Item | Where it comes from | Where it goes (all git-ignored) |
| ---- | ------------------- | -------------------------------- |
| Firebase project id | Firebase console → Project settings → General → *Project ID* | `backend/.env`, `android/firestore.properties`, `ios/Config/FirestoreTarget.local.xcconfig` |
| `google-services.json` | Project settings → Your apps → Android app `com.splitparty.android` → download | `android/app/google-services.json` |
| `GoogleService-Info.plist` | Project settings → Your apps → iOS app `com.splitparty.ios` → download | `ios/SplitParty/GoogleService-Info.plist` |
| Service-account key (backend only) | Project settings → Service accounts → Firebase Admin SDK → **Generate new private key** | `backend/service-account.json` |

Tools: Node 20.12+, Xcode (iOS), Android Studio or JDK 21 (Android). The
Firebase CLI comes with `npm install` in `backend/`; no global install is needed.

Ask whoever owns the Firebase project to add you as a member (Project
settings → Users and permissions) if you can't see it in the console.

## 1. One-time project setup (already done for `a-venge-mo`)

Only needed for a brand-new Firebase project:

1. Firebase console → **Firestore Database** → *Create database* (Native mode,
   any region close to the demo). The repo uses the `(default)` database.
2. Register the Android app (`com.splitparty.android`) and the iOS app
   (`com.splitparty.ios`).
3. Give the Admin SDK service account the roles the deploy needs. The Firebase
   CLI checks that the Firestore API is enabled before it deploys rules and
   indexes, and the account can't do that by default. You'd see
   `403, Permission denied to get service [firestore.googleapis.com]`.
   Open Google Cloud console → **IAM** for the project
   (`https://console.cloud.google.com/iam-admin/iam?project=<project id>`), click
   the pencil on `firebase-adminsdk-…@<project>.iam.gserviceaccount.com`, and
   add the roles **Firebase Admin** and **Service Usage Consumer**. Changes
   take a minute or two to apply. With application-default credentials, your
   own account needs the same access (project Owner/Editor already has it).

## 2. Backend credentials

```sh
cd backend
npm install
cp .env.example .env
```

Edit `backend/.env`: set `FIREBASE_PROJECT_ID` to the project id. Keep
`FIREBASE_TARGET=hosted` and `GOOGLE_APPLICATION_CREDENTIALS=service-account.json`.

**Default: service-account key file.** Generate the key (table above) and save
it as `backend/service-account.json`. A relative path in `.env` is read from
`backend/`; an absolute path to a key outside the repo works too. The file is
git-ignored (`service-account*.json`, `*-firebase-adminsdk-*.json`). Treat it as a password:
don't paste it into chats or tickets, and delete it from the console
(Service accounts → Manage keys) when someone leaves.

**Alternative: your own application-default credentials.** If your Google
account has access to the project, leave the key out:

```sh
gcloud auth application-default login
# in backend/.env:
GOOGLE_APPLICATION_CREDENTIALS=
```

The scripts then use `~/.config/gcloud/application_default_credentials.json`.

The scripts check the configuration **before any database call** and exit with
code 2 and a message naming every problem when it is incomplete or
contradictory. Examples: the target or project id is missing, the project id is still the
template placeholder, `FIRESTORE_EMULATOR_HOST` is still set for hosted, or the
key file is missing, unreadable, not a service-account key, or for another
project. With no key path and no application-default credentials, they refuse too.

## 3. Deploy the rules and indexes

```sh
npm run deploy:firestore
```

This deploys `backend/firestore.rules` and `backend/firestore.indexes.json` to
the project in `backend/.env`, and only there. There is no `.firebaserc` (it is
git-ignored), so a plain `firebase deploy` without `--project` fails instead of
guessing. Check the result in the console: Firestore → **Rules** shows the
repo's rules, and Firestore → **Indexes** shows the two composite indexes
(`transactions: participantIds + createdAt desc`, `parties: merchantId + status + expiresAt`).
New indexes take a few minutes to build. Deploy again whenever the rules or
indexes change; the hosted test run below also deploys them every time.

## 4. Seed the demo database

```sh
npm run seed                # Done: 3 users, 1 merchant, 6 feed transactions.
npm run reset -- --yes      # back to exactly the seeded state (deletes all parties)
```

`npm run reset` without `--yes` refuses against hosted and exits 2.

## 5. Run the apps on phones

Each app reads one git-ignored file. Nothing else changes between hosted and emulator.

**Android** ([details](../android/README.md)):

```sh
cp android/firestore.properties.example android/firestore.properties   # set FIREBASE_PROJECT_ID
cp ~/Downloads/google-services.json android/app/google-services.json
cd android && ./gradlew installDebug
```

**iOS** ([details](../ios/README.md)):

```sh
cp ios/Config/FirestoreTarget.example.xcconfig ios/Config/FirestoreTarget.local.xcconfig   # team id + FIREBASE_PROJECT_ID
cp ~/Downloads/GoogleService-Info.plist ios/SplitParty/GoogleService-Info.plist
open ios/SplitParty.xcodeproj    # pick your iPhone, Run
```

Sign in as `maxi`, `annie` or `christophe`. The phone talks to Firestore over
the internet, so any network works (Wi-Fi or mobile data) and the laptop can be
closed. If a config file is missing or belongs to another project, the app says
so at launch (Android: *Setup required* screen; iOS: message on the login
screen, sign-in disabled) instead of showing an empty feed.

## 6. Run the tests

```sh
npm test                                   # unit tests, no database
npm run test:hosted -- --wipe-demo-db      # acceptance suite against the HOSTED demo database
npm run test:rules                         # security-rules suite, local emulator only (needs Java 21+)
```

`test:hosted` uses the **same database as the demo** and deletes every live
party on it. Without `--wipe-demo-db` it refuses to start. **Never run it during
a demo.** With the flag it:

1. deploys the current rules and indexes (`deploy:firestore`),
2. resets to the seeded state and waits until the composite indexes serve queries,
3. runs `test/acceptance/`, which wipes, seeds, runs a full party, and checks
   that the deployed rules accept a valid party and reject invalid ones when
   written by a client with no credentials, as a phone would,
4. resets to the seeded state again, whether the tests passed, failed or
   were interrupted with Ctrl-C.

A phone opened right afterwards sees exactly the seeded feed.

**Why the rules suite can't run against hosted.** `@firebase/rules-unit-testing`
works only against the emulator: it loads `firestore.rules` into the emulator
and wipes data between tests. `test:rules` always starts its own
emulator under the `demo-split-party-rules` project id, which never maps to a
real project, and ignores `backend/.env`. The suite refuses to run if
`FIREBASE_TARGET=hosted` is set in its environment or no emulator is running.
The hosted rules are covered by the client-write checks in `test:hosted`.

## 7. Check the tree is clean

```sh
git status --short    # nothing listed: config files and keys are all ignored
git check-ignore -v backend/.env backend/service-account.json android/firestore.properties \
  android/app/google-services.json ios/Config/FirestoreTarget.local.xcconfig ios/SplitParty/GoogleService-Info.plist
```

## 8. Opt in: work offline against the emulator

1. `cd backend && npm run emulators` (needs Java 21+; listens on `0.0.0.0:8080`).
2. In `backend/.env` comment out the hosted block and uncomment the emulator
   block (`FIREBASE_TARGET=emulator`, `FIREBASE_PROJECT_ID=demo-split-party`,
   `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`), then `npm run seed`.
3. In the app's config file do the same, with
   `FIRESTORE_EMULATOR_HOST=<laptop LAN IP>:8080` (`ipconfig getifaddr en0`;
   an Android Studio emulator uses `10.0.2.2:8080`). Rebuild. The phone must be
   on the laptop's Wi-Fi. No Firebase config file is needed for the emulator.
4. To go back, restore the hosted block and rebuild.

The Android debug build keeps its cleartext allowance and local-network prompt
so this path works. The hosted path uses neither.

## Security note

The rules check document **shape** only (username login, no Firebase Auth), so
anyone who knows the project id can read and write valid-looking documents.
That is accepted for the PoC.
