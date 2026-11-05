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
None of these files are in the repo; [Sharing the config files with
colleagues](#sharing-the-config-files-with-colleagues) says how to get them.

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

### 3b. Deploy the Demo reset backend (required for the in-app Demo reset)

The **Demo reset** button on the Me tab can't reset the database itself (the
rules forbid it); it calls a server-side reset. Merging the code does not deploy
it: pick **one** of the two options below once per project, and redeploy
whenever `lib/ops.js`, `lib/callableReset.js` or the seed changes. Without
either, the phone shows "Demo reset isn't set up on the server" and logcat
shows `resetDemo failed: NOT_FOUND`.

Both speak the same Firebase callable protocol and run the same `reset()` as
`npm run reset`, so the app behaves identically; only the address differs.

| | A. Cloud Function | B. Free endpoint on Vercel |
| --- | --- | --- |
| Firebase plan | **Blaze** (pay-as-you-go, card needed) | Spark (free) is enough |
| Deploy | `npm run deploy:functions` | `npm run deploy:reset-endpoint` |
| App setting (`android/firestore.properties`) | `RESET_DEMO_URL=` (empty) | `RESET_DEMO_URL=<printed URL>` |
| Admin key lives | inside Google Cloud | as encrypted env vars on Vercel |

After changing `RESET_DEMO_URL`, rebuild and reinstall the app.

**Any seed change needs a redeploy.** The deployed reset bundles its own copy of
`lib/seedData.js` at deploy time, so after editing the seed (for example a
user's `avatar` photo URL) run `npm run seed` *and* redeploy the reset you use:
`npm run deploy:functions` (A) or `npm run deploy:reset-endpoint` (B). Otherwise
the first in-app Demo reset puts the old seed values back.

**Check it is live without resetting anything**: a plain GET on the address
answers **400** when deployed and **404** when it is missing (a POST *does*
reset, so never POST it by hand during a demo):

```sh
curl -s -o /dev/null -w "%{http_code}\n" https://us-central1-<project id>.cloudfunctions.net/resetDemo   # A
curl -s -o /dev/null -w "%{http_code}\n" https://<project id>-demo-reset.vercel.app/api/resetDemo        # B
```

#### A. Cloud Function (projects on Blaze)

```sh
npm run deploy:functions
```

One-time prerequisites (a project **Owner** does these in the console):

- The project is on the **Blaze** plan.
- The APIs `cloudfunctions.googleapis.com`, `cloudbuild.googleapis.com`,
  `artifactregistry.googleapis.com`, `run.googleapis.com` and
  `eventarc.googleapis.com` are enabled. The service account can't enable them
  itself; the deploy then stops with `Permissions denied enabling …` and prints
  the console link to enable it.
- The deploying credential also has **Service Account User**
  ([backend/README.md](../backend/README.md#demo-reset-function-resetdemo)).

#### B. Free endpoint on Vercel (projects without Blaze)

`backend/reset-endpoint/` is a one-file Vercel function
(`api/resetDemo.js`) that runs the reset with the service-account key.

```sh
npx vercel login                 # once per machine (free account)
npm run deploy:reset-endpoint    # from backend/
```

The script copies `lib/` into `reset-endpoint/shared/`, links the folder to the
Vercel project `<project id>-demo-reset`, sets `RESET_PROJECT_ID`,
`RESET_CLIENT_EMAIL` and `RESET_PRIVATE_KEY` (production) from
`backend/service-account.json` without printing them, deploys to production,
checks the GET answers 400 and prints the URL to put in `RESET_DEMO_URL`.
It needs the key file (application-default credentials can't be uploaded).

Before choosing it, know that:

- **The admin key leaves Google**: it is stored, encrypted, in the Vercel
  project's environment variables. Anyone with access to that Vercel account can
  read it. When someone leaves or you stop using the endpoint, delete the key in
  Firebase console → Service accounts → Manage keys, and remove the Vercel
  project. If you rotate the key, run `npm run deploy:reset-endpoint` again.
- The endpoint is **open**, like the Cloud Function: anyone who knows the URL
  can reset the demo database (PoC demo data only).
- Vercel's free **Hobby** plan is for personal, non-commercial use; a team/Pro
  plan may be required for company use.
- The first call after a long idle period can take a second or two longer
  (cold start).

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

## Sharing the config files with colleagues

The config files and the key are git-ignored, so a clone never has them. This
repo is **public**: anything committed is readable by anyone, which is why they
stay out of git. Share them like this instead.

### What can be shared, and how sensitive it is

| File | Sensitivity | Share it? |
| ---- | ----------- | --------- |
| Project id (`a-venge-mo`) | Not secret | Yes, anywhere |
| `google-services.json`, `GoogleService-Info.plist` | Low: they identify the project and ship inside every app build, but with this PoC's shape-only rules anyone holding them can read and write the demo database | Yes, but only through the team vault (below), never in public places |
| `backend/service-account.json` | **High**: full admin access to the project, bypasses the security rules | **No.** Each person gets their own key (or uses their own Google login) |

### Option A (preferred): add the colleague to the Firebase project

Then nobody has to send them files; they download everything themselves.

1. A project owner opens Firebase console → ⚙ **Project settings** →
   **Users and permissions** → **Add member**, enters the colleague's Google
   account and gives the role **Editor** (enough to download the configs, seed and
   deploy; use **Owner** only for people who manage the project).
2. The colleague downloads `google-services.json` and `GoogleService-Info.plist`
   from Project settings → **Your apps** (table at the top of this guide).
3. For the backend, the colleague either:
   - generates **their own** service-account key (Project settings → Service
     accounts → Generate new private key) and saves it as
     `backend/service-account.json`, or
   - skips the key: `gcloud auth application-default login` with the same
     Google account, and `GOOGLE_APPLICATION_CREDENTIALS=` (empty) in `backend/.env`.

### Option B: the colleague has no console access

1. Keep the two app config files in the **team vault**: a shared vault in the
   team's password manager (1Password, Bitwarden, …) or, failing that, a
   private, access-restricted Google Drive folder. Name the entry
   "Split Party — Firebase app configs (a-venge-mo)" and attach both files.
2. For the backend, a project owner generates a **separate** key for that
   colleague (Generate new private key, once per person), puts it in the vault
   entry shared with that person only, and notes in the entry whose key it is
   and when it was created.
3. The colleague copies the files into place (paths in the table at the top).

### Rules for every option

- **Never** send these files through Slack, email, GitHub issues/PRs/gists,
  ticket attachments or AI chats, and never commit them, even to a branch:
  a pushed commit stays in history after it is deleted.
- **One service-account key per person.** A shared key can't be revoked for
  one person without breaking everyone else.
- **When someone leaves** (or a key may have leaked): Google Cloud console →
  IAM & Admin → **Service accounts** → `firebase-adminsdk-…` → **Keys** →
  delete their key, and remove them under Firebase → Users and permissions.
  The app config files don't need rotating for a departure; if they leaked
  publicly, tighten the rules or restrict the API key in Google Cloud console →
  APIs & Services → Credentials.
- Before pushing, `git status --short` must not list any of these files. They
  are ignored, so it shouldn't. If one shows up, stop and check `.gitignore`.

### Keeping a personal master copy

Ignored files exist only in the folder where you put them, so a new clone or
git worktree starts without them. Keep one copy outside every checkout and copy
from it:

```sh
# once: store your copies outside the repo (readable only by you)
mkdir -p ~/secrets/a-venge-mo && chmod 700 ~/secrets/a-venge-mo
cp android/app/google-services.json ios/SplitParty/GoogleService-Info.plist \
   backend/service-account.json ~/secrets/a-venge-mo/

# in each new clone/worktree, from the repo root
S=~/secrets/a-venge-mo
cp $S/google-services.json android/app/
cp $S/GoogleService-Info.plist ios/SplitParty/
cp $S/service-account.json backend/
```

The per-checkout settings files (`backend/.env`, `android/firestore.properties`,
`ios/Config/FirestoreTarget.local.xcconfig`) come from their templates in a
minute, so they don't need to be shared at all.

## Security note

The rules check document **shape** only (username login, no Firebase Auth), so
anyone who knows the project id can read and write valid-looking documents.
That is accepted for the PoC.
