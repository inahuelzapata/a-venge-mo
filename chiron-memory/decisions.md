# decision

A choice made and the reasoning behind it — the path taken over the alternatives.

## Join-code uniqueness via a lock document keyed by the code

What: A party's emoji join code is reserved by writing joinCodes/{merchantId}_{e1e2e3} in the same Firestore transaction as the party; a lock whose expiresAt has passed counts as free and is overwritten · Why: Firestore has no unique constraints, and the PoC has no server job to clean up expired parties, so a doc id plus a transaction (which aborts on concurrent change) is the only race-free guarantee · Where: docs/firestore-schema.md (Join-code uniqueness), firebase/lib/schema.js joinCodeLockId · Learned: in Firestore, enforce uniqueness with a doc whose id is the unique value

## Users are keyed by username

What: users/{username} — the doc id is the username, which is therefore immutable · Why: doc ids are unique for free, and username-only login becomes a single get() with no extra index collection · Where: docs/firestore-schema.md (users)

## Per-person share is never stored; every client computes it

What: Shares come from billCents, tip and memberIds.length with integer math: floor division, leftover cents to the host (memberIds[0]), percent tip rounded half up as floor((bill*pct+50)/100) · Why: a stored share would go stale as people join, and every phone must show the same number · Where: firebase/lib/split.js (reference port for iOS/Android), docs/firestore-schema.md (Split rule)

## Party payments use deterministic transaction ids

What: When the host pays, each joinee's payment is written to transactions/{partyId}_{payerId} with source "party"; seeded feed entries are seed_tx_NN with source "seed" · Why: a retried Pay can never charge twice, and reset can tell seed data from party data · Where: firebase/lib/schema.js partyTransactionId, firebase/lib/ops.js reset
