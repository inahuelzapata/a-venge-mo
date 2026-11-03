# config

Setup and configuration — env vars, flags, how to run the project.

## Firestore target is chosen by FIREBASE_TARGET

What: The seed/reset scripts need FIREBASE_TARGET=emulator|hosted plus FIREBASE_PROJECT_ID; emulator also needs FIRESTORE_EMULATOR_HOST, hosted needs GOOGLE_APPLICATION_CREDENTIALS (a key outside the repo) and refuses to run if FIRESTORE_EMULATOR_HOST is set; reset on hosted needs --yes · Why: the work order forbids writing anywhere unless the target is explicit, and the Admin SDK silently honours FIRESTORE_EMULATOR_HOST · Where: firebase/lib/env.js, firebase/.env.example

## Emulator tests need Java and run serially

What: npm run verify (firebase/) runs the Firestore emulator through emulators:exec, which needs a JDK 21 on PATH (e.g. /opt/homebrew/opt/openjdk@21/bin); emulator test files run with --test-concurrency=1, and the rules test uses its own project id (demo-split-party-rules, hence singleProjectMode false) · Why: the acceptance test asserts exact collection contents and would race with the rules test on shared data · Where: firebase/package.json, firebase/firebase.json
