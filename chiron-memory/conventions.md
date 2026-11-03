# convention

A rule the codebase follows — naming, patterns, and where things live.

## Apps and scripts use the same three Firestore target keys

What: iOS, Android and backend all choose Firestore with FIREBASE_TARGET (emulator|hosted), FIREBASE_PROJECT_ID and FIRESTORE_EMULATOR_HOST, each set in one git-ignored file: backend/.env, ios/Config/FirestoreTarget.local.xcconfig (optional #include? from Base.xcconfig into Info.plist), android/firestore.properties (into BuildConfig); missing values show "not configured" on screen (Android placeholder screen, iOS login footer) · Why: a demo needs phones and scripts on the same database, and the emulator keeps one database per project id, so the ids must match · Where: docs/firestore-target.md · Learned: phones use the laptop's LAN IP, scripts use 127.0.0.1, so the emulator binds 0.0.0.0

## Shared visual reference lives in docs/design

What: The reference screenshots plus palette, avatar colours, row content and bottom-bar order (Home, Pay/Request, Me) are in docs/design/README.md; both app shells match it · Why: the iOS and Android shells are built in parallel and must look the same without sharing code · Where: docs/design/README.md, ios/SplitParty/UI/Theme.swift
