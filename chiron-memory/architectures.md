# architecture

How the system is put together — layers, boundaries, and how data flows.

## Monorepo of three independent native projects

What: ios/ (SwiftUI, iOS 17+), android/ (Kotlin + Compose, API 26+) and backend/ (Firestore rules, emulator, seed/reset) are built separately with no ../ references; shared docs live once in docs/, and the only shared thing is the contract docs/firestore-schema.md · Why: two people build the apps in parallel, and the PRD calls for native apps, not Kotlin Multiplatform · Where: docs/repo-structure.md
