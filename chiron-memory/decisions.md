# decision

A choice made and the reasoning behind it — the path taken over the alternatives.

## Join-code uniqueness via a lock document keyed by the code

What: A party's emoji join code is reserved by writing joinCodes/{merchantId}_{e1e2e3} in the same Firestore transaction as the party; a lock whose expiresAt has passed counts as free and is overwritten · Why: Firestore has no unique constraints, and the PoC has no server job to clean up expired parties, so a doc id plus a transaction (which aborts on concurrent change) is the only race-free guarantee · Where: docs/firestore-schema.md (Join-code uniqueness), backend/lib/schema.js joinCodeLockId · Learned: in Firestore, enforce uniqueness with a doc whose id is the unique value

## Users are keyed by username

What: users/{username} — the doc id is the username, which is therefore immutable · Why: doc ids are unique for free, and username-only login becomes a single get() with no extra index collection · Where: docs/firestore-schema.md (users)

## Per-person share is never stored; every client computes it

What: Shares come from billCents, tip and memberIds.length with integer math: floor division, leftover cents to the host (memberIds[0]), percent tip rounded half up as floor((bill*pct+50)/100) · Why: a stored share would go stale as people join, and every phone must show the same number · Where: backend/lib/split.js (reference port for iOS/Android), docs/firestore-schema.md (Split rule)

## Party payments use deterministic transaction ids

What: When the host pays, each joinee's payment is written to transactions/{partyId}_{payerId} with source "party"; seeded feed entries are seed_tx_NN with source "seed" · Why: a retried Pay can never charge twice, and reset can tell seed data from party data · Where: backend/lib/schema.js partyTransactionId, backend/lib/ops.js reset

## iOS app uses a memory-only Firestore cache

What: FirebaseBootstrap sets MemoryCacheSettings, and LiveDataStore shows an error card when a list is only fromCache: at once if NWPathMonitor reports no network, otherwise after a 4 s grace; on network return it calls disableNetwork/enableNetwork to skip Firestore's retry backoff · Why: the work order wants a visible error state offline and identical data on all demo phones; a disk cache would silently show stale rows instead · Where: ios/SplitParty/Data/FirebaseBootstrap.swift, ios/SplitParty/App/LiveDataStore.swift · Learned: Firestore listeners never error when offline, they just deliver cached snapshots, so "unreachable" must be derived from metadata.isFromCache

## Feed rows use display names, never "You"

What: Both apps render every feed row as "Payer paid Payee for memo" with display names, even for the signed-in user · Why: the checkpoint requires the 3 phones to show identical rows; "You" would differ per phone · Where: ios/SplitParty/UI/TransactionList.swift, docs/design/README.md

## Avatars are initials on a fixed colour per avatar key

What: users.avatar keys map to fixed colours (avatar_maxi #0087E0, avatar_annie #E5487D, avatar_christophe #2EAF6F, unknown #8E8E93) drawn with initials; no image assets · Why: no avatar art exists, and fixed values let Android match iOS exactly · Where: ios/SplitParty/UI/AvatarView.swift, docs/design/README.md

## iOS snapshot and UI tests run on an in-memory fixture, not Firestore

What: Screens read through the PaymentDataSource protocol; FixtureDataSource (Debug only) serves the seeded users and payments with ages relative to now; UI tests switch to it with -UITestFixture seeded|empty|unreachable, -UITestResetSession, -UITestInsertIncomingPaymentAfter · Why: tests need no Java/emulator and stay deterministic; relative ages keep "2 days ago" stable in snapshots · Where: ios/SplitParty/Data/FixtureDataSource.swift, ios/SplitParty/App/AppModel.swift UITestLaunch · Learned: the fixture must stay in step with backend/lib/seedData.js by hand
