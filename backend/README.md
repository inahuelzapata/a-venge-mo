# backend — Firestore for Split Party

Everything server-side for the PoC: Firestore security rules and indexes, the
Firestore emulator configuration, and the seed/reset scripts with their
tests. The data contract the rules enforce is
[`docs/firestore-schema.md`](../docs/firestore-schema.md).

This is the **only** place the Firestore emulator is configured and started;
the apps connect to it.

| Path | What |
| ---- | ---- |
| `firestore.rules`, `firestore.indexes.json` | Deployed to the hosted project; loaded by the emulator |
| `firebase.json` | Emulator config (Firestore on `0.0.0.0:8080`, so phones on the LAN can reach it) |
| `scripts/seed.js`, `scripts/reset.js` | Seed and reset commands |
| `lib/` | Target selection (`env.js`), seed data, split rule, validators |
| `test/` | Unit tests and emulator acceptance/rules tests |

## Setup

Requires Node 20.12+, and a JDK 21+ on `PATH` for the emulator.

```sh
cd backend
npm install
cp .env.example .env        # then pick emulator or hosted in .env
```

## Choosing the target

`backend/.env` is git-ignored; no credentials are ever committed. Same keys as
the apps; see [Firestore target](../docs/firestore-target.md).

| Variable                         | Emulator               | Hosted |
| -------------------------------- | ---------------------- | ------ |
| `FIREBASE_TARGET`                | `emulator`             | `hosted` |
| `FIREBASE_PROJECT_ID`            | `demo-split-party`     | your Firebase project id |
| `FIRESTORE_EMULATOR_HOST`        | `127.0.0.1:8080`       | must be **unset** |
| `GOOGLE_APPLICATION_CREDENTIALS` | –                      | path to a service-account key **outside** the repo |

If anything is missing, the scripts exit with a message that names it and
write nothing.

## Commands

```sh
npm run emulators          # local only: start the Firestore emulator
npm run seed               # 3 friends (Maxi, Annie, Christophe), Tony's, seeded feed; safe to re-run
npm run reset              # delete all parties and party transactions, restore balances and feed
npm run reset -- --yes     # required when FIREBASE_TARGET=hosted
```

Seed is idempotent: every document has a fixed id and fixed values. Reset
leaves the database exactly as it is after a fresh seed.

Hosted setup: deploy the rules and indexes once with
`npx firebase deploy --only firestore --project <id>`.

## Tests

```sh
npm test         # split rule and validator unit tests
npm run verify   # starts the emulator, then runs the acceptance and security-rules tests
```
