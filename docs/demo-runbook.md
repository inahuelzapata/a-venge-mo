# Demo runbook

How to seed, reset and run the 3-phone Split Party demo. Every command below
runs from `backend/` unless it says otherwise.

## 0. One-time setup

Follow the [Integration & Setup Guide](integration-setup-guide.md): config
files for the backend and both apps, the service-account key, and
`npm run deploy:firestore` so the hosted project has the repo's rules and
indexes, and the Demo reset backend (step 3b: `npm run deploy:functions` on
Blaze, or `npm run deploy:reset-endpoint` plus `RESET_DEMO_URL` without Blaze;
without one of them the in-app **Demo reset** button fails on every phone).

## 1. One database for everything

Phones and scripts must point at the **same** Firestore. By default that is the
hosted project: `FIREBASE_TARGET=hosted` and the same `FIREBASE_PROJECT_ID` in
`backend/.env` and in each phone's config. Phones can be on any network,
including mobile data, and the laptop is only needed to run seed/reset.

**Before the demo**, don't run `npm run test:hosted`: it wipes every live party
on this same database. If it was run, it has already put the database back in
the seeded state.

## 2. Offline alternative: the emulator (opt-in)

If there is no internet, switch every config file to its emulator block (see
[Firestore target](firestore-target.md)) and start the emulator:

```sh
npm run emulators         # leave running; listens on 0.0.0.0:8080
```

Phones use `FIRESTORE_EMULATOR_HOST=<laptop LAN IP>:8080` (`ipconfig getifaddr en0`)
and must share the laptop's Wi-Fi, on a network that allows
device-to-device traffic (guest networks often block it; use a phone hotspot).

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
2. Maxi (host) starts a Split Party at Tony's and reads out the two-emoji code.
3. Annie and Christophe enter the code within 5 minutes.
4. Maxi enters the bill and tip; all three phones show the same shares.
5. Maxi taps **Pay**; balances update and the payments appear in the feed.

## 5. Reset between runs

```sh
npm run reset -- --yes    # hosted (refuses without --yes)
npm run reset             # emulator
```

Reset deletes every party, join-code lock and party transaction, and restores
balances and the feed exactly as after a fresh seed.

## Troubleshooting

- **Script prints `Firestore target is not configured. Nothing was written.`**:
  `backend/.env` is missing or incomplete. The message names each missing
  variable.
- **Phone shows "Firestore target not configured"**: the app's local config
  file is missing; see the app's README.
- **Phone shows "Setup required" / a config-file message**: the Firebase
  config file is missing or from another project; see the app's README.
- **Script says the key file does not exist / is for another project**: put the
  key from the console at `backend/service-account.json` (see the guide).
- **A query fails with a missing-index error**: run `npm run deploy:firestore`
  and wait for the indexes to finish building in the console.
- **Phone cannot reach the emulator**: check the LAN IP, that the emulator is
  running, and that the macOS firewall allows incoming connections to Java.
