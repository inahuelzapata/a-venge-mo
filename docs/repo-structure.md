# Repository structure (convention)

This repository is a **monorepo of three independent native projects**. It is
*not* Kotlin Multiplatform or Compose Multiplatform: the apps share nothing at
build time. The only thing they share is the data contract,
[`docs/firestore-schema.md`](firestore-schema.md).

```
.
├── ios/        SwiftUI app (iOS 17+). Opened, built and run from this folder only.
├── android/    Kotlin + Jetpack Compose app (API 26+). Opened, built and run from this folder only.
├── backend/    Firestore rules and indexes, emulator config, seed/reset scripts and their tests.
├── docs/       Shared documentation: PRD, Firestore schema, demo runbook, this file.
└── chiron-memory/  Project memory for AI-assisted sessions (decisions, conventions, gotchas).
```

## Rules for new work

1. **Platform-specific code goes inside its platform folder.** iOS code,
   assets and Swift packages go under `ios/`, Android code and Gradle modules
   under `android/`, and scripts, rules, indexes and emulator settings under
   `backend/`. A new native piece (for example a web admin) gets its **own
   new top-level folder** with its own README; do not nest it inside another
   platform's folder.
2. **No build-time sharing.** No project may reference a file outside its own
   folder: no `../` paths in Xcode, Gradle or npm config, no symlinks, no
   shared generated code. Deleting `ios/` must not affect the Android build,
   and the reverse.
3. **Shared knowledge lives in `docs/`, exactly once.** Docs that more than
   one piece needs (PRD, schema, runbook, conventions) go in `docs/`.
   Platform READMEs **link** to them; they never copy them. Docs that only
   matter to one platform stay in that platform's README.
4. **The schema is the contract.** A change to collections or fields updates
   `docs/firestore-schema.md`, `backend/firestore.rules` and
   `backend/lib/validate.js` together, and then each app.
5. **Only `backend/` runs the Firestore emulator.** The apps never carry their
   own emulator setup; they connect to the one started from `backend/`.
6. **One way to choose the Firestore target**, the same in every piece. See
   [Firestore target](firestore-target.md).
7. **No secrets or Firebase platform config in git.** `GoogleService-Info.plist`,
   `google-services.json`, service-account keys and `.env` files are
   git-ignored. Each folder ships a template or README that explains the
   missing step.
8. **No CI** for the hackathon. Each piece is built and verified locally.
9. **Build output stays out of git.** The root `.gitignore` covers all three
   stacks; add to it when a new tool writes local artifacts.
