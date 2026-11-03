# aven-mo
Similar like Venmo app that demoes nearby payments

## Split Party data model (Firestore)

The full contract for both mobile apps is in
[`docs/firestore-schema.md`](docs/firestore-schema.md). In short:

| Collection     | Key                          | Holds |
| -------------- | ---------------------------- | ----- |
| `users`        | username (`maxi`)            | `username`, `displayName`, `avatar`, `balanceCents`, `friendIds` |
| `merchants`    | `tonys-greenwich`            | `name`, `city`, `logoRef`, `offer`, `geofence {center, radiusMeters}` |
| `parties`      | auto id                      | `hostId`, `merchantId`, `joinCode` (3 emojis), `status` (`open`/`paid`/`expired`), `createdAt`, `expiresAt` (+5 min), `paidAt`, `billCents`, `tip`, `memberIds`, `members {uid: {joinedAt, paid}}`, `wrongCodeAttempts {uid: n}` |
| `joinCodes`    | `{merchantId}_{emojis}`      | Lock that keeps a join code unique among active parties at a merchant |
| `transactions` | `seed_tx_NN` / `{partyId}_{payerId}` | `payerId`, `payeeId`, `participantIds`, `amountCents`, `memo`, `merchantId`, `merchantName`, `partyId`, `createdAt`, `likesCount`, `commentsCount`, `source` |

- All money is integer cents.
- The per-person share is **not stored**. Every phone computes it from the
  bill, tip and headcount, leftover cents go to the host (see
  `firebase/lib/split.js`).
- Feed query: `transactions orderBy createdAt desc`. Me tab:
  `where participantIds array-contains <uid> orderBy createdAt desc`.

### Seed and reset

The tooling lives in [`firebase/`](firebase) (Node 20.12+).

```sh
cd firebase
npm install
cp .env.example .env        # then pick emulator or hosted in .env
```

Choose the target via environment (`firebase/.env` is git-ignored; no
credentials are ever committed):

| Variable                         | Emulator               | Hosted |
| -------------------------------- | ---------------------- | ------ |
| `FIREBASE_TARGET`                | `emulator`             | `hosted` |
| `FIREBASE_PROJECT_ID`            | `demo-split-party`     | your Firebase project id |
| `FIRESTORE_EMULATOR_HOST`        | `127.0.0.1:8080`       | must be **unset** |
| `GOOGLE_APPLICATION_CREDENTIALS` | –                      | path to a service-account key **outside** the repo |

If anything is missing, the scripts exit with a message that names it and
write nothing.

```sh
npm run emulators          # local only: start the Firestore emulator (needs Java 21+)
npm run seed               # 3 friends (Maxi, Annie, Christophe), Tony's, seeded feed; safe to re-run
npm run reset              # delete all parties and party transactions, restore balances and feed
npm run reset -- --yes     # required when FIREBASE_TARGET=hosted
```

Seed is idempotent: every document has a fixed id and fixed values. Reset
leaves the database exactly as it is after a fresh seed.

Hosted setup: deploy the rules and indexes once with
`npx firebase deploy --only firestore --project <id>`.

### Tests

```sh
npm test         # split rule and validator unit tests
npm run verify   # starts the emulator, then runs the acceptance and security-rules tests
```
