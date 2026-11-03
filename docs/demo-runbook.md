# Demo runbook

How to seed, reset and run the 3-phone Split Party demo. Every command below
runs from `backend/` unless it says otherwise.

## 0. One-time setup

```sh
cd backend
npm install
cp .env.example .env      # keep the emulator block, or switch to the hosted block
```

Requirements: Node 20.12+, and for the emulator a JDK 21+ on `PATH`.
For the apps, see [`ios/README.md`](../ios/README.md) and
[`android/README.md`](../android/README.md).

## 1. Pick one database for everything

Phones and scripts must point at the **same** Firestore. The switch works the
same way everywhere; see [Firestore target](firestore-target.md).

| Demo on…          | `backend/.env`                                          | Each phone's config |
| ----------------- | ------------------------------------------------------- | ------------------- |
| Local emulator    | `FIREBASE_TARGET=emulator`, `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080` | `FIREBASE_TARGET=emulator`, `FIRESTORE_EMULATOR_HOST=<laptop LAN IP>:8080` |
| Hosted project    | `FIREBASE_TARGET=hosted`, project id, key path           | `FIREBASE_TARGET=hosted`, same project id |

Find the laptop's LAN IP with `ipconfig getifaddr en0` (macOS). Phones and
laptop must be on the same Wi-Fi network, and that network must allow
device-to-device traffic (guest networks often block it; use a phone hotspot
if so).

## 2. Start the emulator (emulator demo only)

```sh
npm run emulators         # leave running; listens on 0.0.0.0:8080
```

## 3. Seed

```sh
npm run seed
```

Result: users `maxi`, `annie`, `christophe` (mutual friends), merchant
`tonys-greenwich` (Tony's), six seeded feed transactions, zero parties. Safe
to run again.

## 4. Run the demo

1. Install the app on three phones (any mix of iPhone and Android) and log in
   as `maxi`, `annie` and `christophe`.
2. Maxi (host) starts a Split Party at Tony's and reads out the three-emoji code.
3. Annie and Christophe enter the code within 5 minutes.
4. Maxi enters the bill and tip; all three phones show the same shares.
5. Maxi taps **Pay**; balances update and the payments appear in the feed.

## 5. Reset between runs

```sh
npm run reset             # emulator
npm run reset -- --yes    # hosted (asks for explicit confirmation)
```

Reset deletes every party, join-code lock and party transaction, and restores
balances and the feed exactly as after a fresh seed.

## Troubleshooting

- **Script prints `Firestore target is not configured. Nothing was written.`**:
  `backend/.env` is missing or incomplete. The message names each missing
  variable.
- **Phone shows "Firestore target not configured"**: the app's local config
  file is missing; see the app's README.
- **Phone cannot reach the emulator**: check the LAN IP, that the emulator is
  running, and that the macOS firewall allows incoming connections to Java.
