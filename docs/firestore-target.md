# Firestore target (hosted vs emulator)

Every piece of the repo picks its database the same way: **three keys, the
same names everywhere, set in one git-ignored file per piece.**

| Key | Values | Meaning |
| --- | ------ | ------- |
| `FIREBASE_TARGET` | `emulator` or `hosted` | Which Firestore to use. Empty or missing means "not configured": the scripts refuse to run, and the apps show a warning on screen. |
| `FIREBASE_PROJECT_ID` | `demo-split-party` for the emulator; your project id for hosted | The emulator keeps a separate database per project id, so phones and scripts must use the **same** id. |
| `FIRESTORE_EMULATOR_HOST` | `host:8080`, emulator only | Where the emulator listens. Must be empty for `hosted`. |

## Where each piece sets them

| Piece | The one file to edit (git-ignored) | Template | Takes effect |
| ----- | ---------------------------------- | -------- | ------------ |
| Backend scripts | `backend/.env` | `backend/.env.example` | Next `npm run seed` / `reset` |
| iOS app | `ios/Config/FirestoreTarget.local.xcconfig` | `ios/Config/FirestoreTarget.example.xcconfig` | Next build and run in Xcode |
| Android app | `android/firestore.properties` | `android/firestore.properties.example` | Next Gradle build and run |

The backend also needs `GOOGLE_APPLICATION_CREDENTIALS` for hosted (a
service-account key kept outside the repo). The apps don't need it: they use
their Firebase platform config file instead (see each app's README).

## Pointing phones and scripts at the same database

**Emulator demo.** There is one emulator, started from `backend/` with
`npm run emulators`. It listens on all interfaces, port 8080.

- `backend/.env`: `FIREBASE_TARGET=emulator`, `FIREBASE_PROJECT_ID=demo-split-party`,
  `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080` (the scripts run on the laptop).
- Each phone: `FIREBASE_TARGET=emulator`, `FIREBASE_PROJECT_ID=demo-split-party`,
  `FIRESTORE_EMULATOR_HOST=<laptop LAN IP>:8080`. A physical phone cannot use
  `127.0.0.1`. Get the IP with `ipconfig getifaddr en0`.

**Hosted demo.** Everywhere: `FIREBASE_TARGET=hosted`, the same
`FIREBASE_PROJECT_ID`, and `FIRESTORE_EMULATOR_HOST` empty. The apps' Firebase
config files must come from that same project.

The apps never start or configure an emulator of their own.
