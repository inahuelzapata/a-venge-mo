# backend — Firestore for Split Party

Everything server-side for the PoC: Firestore security rules and indexes, the
Firestore emulator configuration, the seed/reset scripts, the `resetDemo`
Cloud Function behind the apps' Demo reset button, and their tests. The data contract the rules enforce is
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
| `functions/index.js` | The `resetDemo` callable: runs the same reset as `npm run reset` with admin rights |
| `scripts/build-functions.js` | Copies `lib/{ops,schema,seedData}.js` into `functions/shared/` (generated, git-ignored) before deploy and tests |
| `scripts/deploy-firestore.js`, `scripts/deploy-functions.js` | Deploy rules + indexes / the `resetDemo` function to `FIREBASE_PROJECT_ID` |
| `reset-endpoint/`, `scripts/deploy-reset-endpoint.js`, `lib/callableReset.js` | The same reset as a callable-protocol endpoint on Vercel, for projects without Blaze |
| `scripts/test-hosted.js` | Hosted acceptance run: deploy, reset, test, reset |
| `lib/` | Target and credential checks (`env.js`), deploy, seed data, split rule, validators |
| `test/unit/` | Unit tests (no database) |
| `test/acceptance/` | Acceptance suite, run by `test:hosted` |
| `test/rules/` | Security-rules suite, emulator only |
| `test/functions/` | `resetDemo` suite, on the Firestore + Functions emulators |

## Setup

Requires Node 20.12+. The emulator, `test:rules` and `test:functions` also need a JDK 21+ on `PATH`.

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
npm run deploy:functions   # hosted: deploy the resetDemo callable (us-central1) to FIREBASE_PROJECT_ID
npm run seed               # 3 friends (Maxi, Annie, Christophe), Tony's, seeded feed; safe to re-run
npm run reset -- --yes     # delete all parties and party transactions, restore balances and feed
                           # (--yes is required for hosted; without it reset refuses and exits 2)
npm run emulators          # emulator opt-in only: start the local Firestore emulator
```

Seed is idempotent: every document has a fixed id and fixed values. Reset
leaves the database exactly as it is after a fresh seed.

The deploy always goes to `FIREBASE_PROJECT_ID` from `.env`. There is no
`.firebaserc`, and it is git-ignored, so don't run `firebase use`.

## Demo reset function (`resetDemo`)

The apps' **Demo reset** button can't reset the database itself: the rules
forbid clients from deleting parties and transactions. It calls this Cloud
Function instead, which runs `reset()` from `lib/ops.js` (the exact code behind
`npm run reset`) with admin rights.

| | |
| --- | --- |
| Callable name | `resetDemo` |
| Region | `us-central1` (the Firebase SDKs' default, so the apps need no region setting) |
| Project | the same Firebase project as the Firestore database (`FIREBASE_PROJECT_ID`) |
| Arguments | none |
| Success | `{ ok: true, resetAt: "<ISO-8601 time>", removed: {…}, seeded: {…} }` |
| Failure | callable error `internal`, message says the reset did not complete and may be partial; calling again is safe and finishes it |
| Auth | **none**: an open callable, anyone with the project config can invoke it (PoC demo database only) |

Deploy (after `npm run deploy:firestore`):

```sh
npm run deploy:functions
```

It needs the project on the **Blaze** plan (Cloud Functions requires it) and,
besides the deploy roles above, *Service Account User* on the credential so it
can deploy as the runtime service account. The deploy makes the function
publicly invokable; an organisation policy that forbids `allUsers` will block
that. Reset is idempotent, so two taps at once both succeed. A cold start
adds a few seconds to the first call after the function has been idle.

### Without Blaze: the reset endpoint on Vercel

`reset-endpoint/api/resetDemo.js` serves the same reset over the same callable
protocol (`lib/callableReset.js`: POST `{data:null}` → `{result:{ok,…}}`, errors
as `{error:{status,message}}`, GET → 400), so the apps call it by URL with the
Firebase Functions SDK (`RESET_DEMO_URL` in `android/firestore.properties`).
Deploy with `npm run deploy:reset-endpoint`; setup, trade-offs and the key's
handling are in [setup guide step 3b](../docs/integration-setup-guide.md#3b-deploy-the-demo-reset-backend-required-for-the-in-app-demo-reset).

`functions/shared/` is generated from `lib/` by `npm run build:functions`
(also run automatically by the deploy and by `test:functions`). Edit `lib/`,
never the copy.

## Tests

```sh
npm test                                 # unit tests: split rule, validators, target/credential checks
npm run test:hosted -- --wipe-demo-db    # acceptance suite on the HOSTED demo database
npm run test:rules                       # security-rules suite on a throwaway emulator
npm run test:functions                   # resetDemo callable on throwaway Firestore + Functions emulators
npm run test:reset-endpoint              # the Vercel reset endpoint on a throwaway Firestore emulator
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

`test:functions` (JDK 21 on `PATH`) seeds, runs a party to paid, then calls
`resetDemo` over HTTP as an app would and checks that the database equals
the fresh-seed state, that a call on a seeded database changes nothing, that
two simultaneous calls both succeed, and that `npm run reset` and `resetDemo`
leave identical databases. It uses the `demo-split-party-functions` emulator
project and ignores `.env`.
