# Firestore target (hosted vs emulator)

Every piece of the repo picks its database the same way: **three keys, the
same names everywhere, set in one git-ignored file per piece.** The **hosted**
Firebase project is the default in every template; the local emulator is an
explicit opt-in for offline work. Step-by-step setup:
[Integration & Setup Guide](integration-setup-guide.md).

| Key | Values | Meaning |
| --- | ------ | ------- |
| `FIREBASE_TARGET` | `hosted` (default) or `emulator` | Which Firestore to use. Empty or missing means "not configured": the scripts refuse to run, and the apps show a warning on screen. |
| `FIREBASE_PROJECT_ID` | your Firebase project id for hosted; `demo-split-party` for the emulator | The emulator keeps a separate database per project id, so phones and scripts must use the **same** id. |
| `FIRESTORE_EMULATOR_HOST` | `host:8080`, emulator only | Where the emulator listens. Must be empty for `hosted`. |

## Where each piece sets them

| Piece | The one file to edit (git-ignored) | Template | Takes effect |
| ----- | ---------------------------------- | -------- | ------------ |
| Backend scripts and tests | `backend/.env` | `backend/.env.example` | Next `npm run seed` / `reset` / `deploy:firestore` / `test:hosted` |
| iOS app | `ios/Config/FirestoreTarget.local.xcconfig` | `ios/Config/FirestoreTarget.example.xcconfig` | Next build and run in Xcode |
| Android app | `android/firestore.properties` | `android/firestore.properties.example` | Next Gradle build and run |

The backend also needs a credential for hosted: by default the service-account
key at `backend/service-account.json` (`GOOGLE_APPLICATION_CREDENTIALS=service-account.json`,
git-ignored), or, as an option, the developer's own application-default
credentials (`gcloud auth application-default login`, key path left empty).
The apps don't need it: they use their Firebase platform config file instead
(`google-services.json`, `GoogleService-Info.plist`; see each app's README).

## Pointing phones and scripts at the same database

**Hosted demo (default).** Everywhere: `FIREBASE_TARGET=hosted`, the same
`FIREBASE_PROJECT_ID`, and `FIRESTORE_EMULATOR_HOST` empty. The apps' Firebase
config files must come from that same project. Phones can be on any network,
including mobile data; no laptop is involved. Rules and indexes reach the
project only through `npm run deploy:firestore`, which always deploys to
`FIREBASE_PROJECT_ID` from `backend/.env`.

**Emulator (opt-in, offline).** There is one emulator, started from `backend/` with
`npm run emulators`. It listens on all interfaces, port 8080.

- `backend/.env`: `FIREBASE_TARGET=emulator`, `FIREBASE_PROJECT_ID=demo-split-party`,
  `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080` (the scripts run on the laptop).
- Each phone: `FIREBASE_TARGET=emulator`, `FIREBASE_PROJECT_ID=demo-split-party`,
  `FIRESTORE_EMULATOR_HOST=<laptop LAN IP>:8080`. A physical phone cannot use
  `127.0.0.1`. Get the IP with `ipconfig getifaddr en0`.

The apps never start or configure an emulator of their own.

## Tests and targets

| Command (from `backend/`) | Database |
| ------------------------- | -------- |
| `npm test` | none (unit tests) |
| `npm run test:hosted -- --wipe-demo-db` | the hosted demo database in `backend/.env`; deploys rules + indexes first, resets before and after |
| `npm run test:rules` | always a throwaway emulator (`demo-split-party-rules`); ignores `backend/.env`, because the rules-testing library only works against the emulator |
