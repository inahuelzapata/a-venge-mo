# backend — Firestore for Split Party

Everything server-side for the PoC: Firestore security rules and indexes, the
Firestore emulator configuration, and the seed/reset scripts with their
tests. The data contract the rules enforce is
[`docs/firestore-schema.md`](../docs/firestore-schema.md).

The default target is the **hosted** Firebase project. The Firestore
emulator is an opt-in for offline work, and this is the **only** place it is
configured and started. Full walkthrough:
[Integration & Setup Guide](../docs/integration-setup-guide.md).

| Path | What |
| ---- | ---- |
| `firestore.rules`, `firestore.indexes.json` | Deployed to the hosted project with `npm run deploy:firestore`; loaded by the emulator |
| `firebase.json` | Rules/index paths and emulator config (Firestore on `0.0.0.0:8080`, so phones on the LAN can reach it) |
| `scripts/seed.js`, `scripts/reset.js` | Seed and reset commands |
| `scripts/deploy-firestore.js` | Deploys rules and indexes to `FIREBASE_PROJECT_ID` |
| `scripts/test-hosted.js` | Hosted acceptance run: deploy, reset, test, reset |
| `lib/` | Target and credential checks (`env.js`), deploy, seed data, split rule, validators |
| `test/unit/` | Unit tests (no database) |
| `test/acceptance/` | Acceptance suite, run by `test:hosted` |
| `test/rules/` | Security-rules suite, emulator only |

## Setup

Requires Node 20.12+. The emulator and `test:rules` also need a JDK 21+ on `PATH`.

```sh
cd backend
npm install
cp .env.example .env        # set FIREBASE_PROJECT_ID
# save the service-account key as backend/service-account.json (git-ignored)
```

## Choosing the target

`backend/.env` is git-ignored; no credentials are ever committed. Same keys as
the apps; see [Firestore target](../docs/firestore-target.md).

| Variable                         | Hosted (default) | Emulator (opt-in) |
| -------------------------------- | ---------------- | ----------------- |
| `FIREBASE_TARGET`                | `hosted`         | `emulator` |
| `FIREBASE_PROJECT_ID`            | your Firebase project id | `demo-split-party` |
| `FIRESTORE_EMULATOR_HOST`        | must be **unset/empty** | `127.0.0.1:8080` |
| `GOOGLE_APPLICATION_CREDENTIALS` | `service-account.json` (key from Firebase console → Project settings → Service accounts → *Generate new private key*, saved in `backend/`; relative paths resolve from `backend/`) | – |

**Alternative credential:** leave `GOOGLE_APPLICATION_CREDENTIALS` empty and
run `gcloud auth application-default login`; the scripts then use your own
application-default credentials.

If anything is missing or contradictory, every script exits with code 2 and a
message naming each problem, before any database call, and writes nothing.
That covers: no target or project id, the template placeholder id, the emulator host
still set for hosted, a key file that is missing, unreadable, not a
service-account key or for another project, or no credentials at all.

## Commands

```sh
npm run deploy:firestore   # hosted: deploy firestore.rules + firestore.indexes.json to FIREBASE_PROJECT_ID
npm run seed               # 3 friends (Maxi, Annie, Christophe), Tony's, seeded feed; safe to re-run
npm run reset -- --yes     # delete all parties and party transactions, restore balances and feed
                           # (--yes is required for hosted; without it reset refuses and exits 2)
npm run emulators          # emulator opt-in only: start the local Firestore emulator
```

Seed is idempotent: every document has a fixed id and fixed values. Reset
leaves the database exactly as it is after a fresh seed.

The deploy always goes to `FIREBASE_PROJECT_ID` from `.env`. There is no
`.firebaserc`, and it is git-ignored, so don't run `firebase use`.

## Tests

```sh
npm test                                 # unit tests: split rule, validators, target/credential checks
npm run test:hosted -- --wipe-demo-db    # acceptance suite on the HOSTED demo database
npm run test:rules                       # security-rules suite on a throwaway emulator
```

`test:hosted` runs against **the demo database** and deletes every live party
on it, so it refuses to start without `--wipe-demo-db`. Don't run it during a
demo. It deploys the current rules and indexes, resets to the seeded state, waits
for the composite indexes, runs `test/acceptance/`, and resets again whether
the run passed, failed or was interrupted. Extra `node --test` flags pass
through, e.g. `npm run test:hosted -- --wipe-demo-db --test-name-pattern=seed`.

`test:rules` can't run against hosted: `@firebase/rules-unit-testing` only
works with the emulator (it loads the rules file into it and clears data
between tests). It ignores `.env`, always uses the `demo-split-party-rules`
emulator project, and refuses to run if `FIREBASE_TARGET=hosted` is set in its
environment or no emulator is running. `PERMISSION_DENIED` lines in its output
are expected: they come from the invalid parties being rejected. The deployed
hosted rules are checked by `test:hosted`, which writes one valid and several
invalid parties as an unauthenticated client.
