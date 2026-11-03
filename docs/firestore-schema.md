# Split Party — Firestore schema (PoC)

This document is the contract between the iOS app, the Android app and the
seed/reset scripts in [`backend/`](../backend). Both apps read and write
**exactly** these collections and fields. If a field is not listed here, do
not write it.

Collections:

| Collection     | Doc id                         | What it holds                                         |
| -------------- | ------------------------------ | ----------------------------------------------------- |
| `users`        | username, e.g. `maxi`          | Account, mock balance, friends                        |
| `merchants`    | slug, e.g. `tonys-greenwich`   | Partner merchant: name, logo, offer, geofence         |
| `parties`      | auto id                        | One Split Party: host, members, bill, tip, status     |
| `joinCodes`    | `{merchantId}_{e1}{e2}{e3}`    | Lock that keeps a join code unique per merchant       |
| `transactions` | auto id, or `{partyId}_{payerId}` | Payments shown in the feed and on the Me tab       |

Conventions that apply everywhere:

- **Money is always an integer number of cents** (`int64`). `$12.34` is stored
  as `1234`. Never store floats or strings for money; never store dollars.
  Field names that hold money end in `Cents`.
- **Timestamps** are Firestore `Timestamp` values. Clients write
  `serverTimestamp()` for creation times except where noted.
- **Ids** are Firestore document ids (strings). A field ending in `Id` holds a
  document id from the named collection; `…Ids` is an array of them.
- "Derived" means the value is computed from other fields. Derived values are
  either **not stored** (each client computes them) or stored only as a
  denormalised copy that must be written in the same batch/transaction as its
  source. Each case is marked below.

---

## `users/{userId}`

One document per account. The document id **is** the username, which makes
usernames unique for free (Firestore doc ids are unique within a collection)
and lets username-only login be a single `get("users/{username}")`.
Usernames are therefore immutable.

| Field          | Type             | Description |
| -------------- | ---------------- | ----------- |
| `username`     | string           | Login key. Lower-case, matches `^[a-z0-9_]{3,20}$`. Always equal to the document id. |
| `displayName`  | string           | Name shown in the UI, e.g. `"Maxi"`. 1–40 chars. |
| `avatar`       | string           | Avatar asset key bundled in both apps, e.g. `"avatar_maxi"`. Apps map the key to an image; unknown keys fall back to initials. |
| `balanceCents` | int              | Mock wallet balance in cents. Changed only by a party payment (see *Paying a party*). May go negative; the PoC does not block payments. |
| `friendIds`    | array\<string\>  | User ids of this user's friends. Friendship is mutual: if `a` lists `b`, `b` lists `a`. Never contains the user's own id. |

Login (username only, no password): `users/{username.trim().toLowerCase()}`,
and the account exists if that document exists.

Seeded accounts (see `backend/lib/seedData.js` for the exact values):

| id           | displayName | avatar              | balanceCents | friendIds                |
| ------------ | ----------- | ------------------- | ------------ | ------------------------ |
| `maxi`       | Maxi        | `avatar_maxi`       | 25000        | `annie`, `christophe`    |
| `annie`      | Annie       | `avatar_annie`      | 18050        | `maxi`, `christophe`     |
| `christophe` | Christophe  | `avatar_christophe` | 32075        | `maxi`, `annie`          |

---

## `merchants/{merchantId}`

One document per partner merchant. Both apps read merchant data (name, logo,
offer, geofence) from here and never hard-code it. Clients only read this
collection; the seed script writes it.

| Field      | Type      | Description |
| ---------- | --------- | ----------- |
| `name`     | string    | Display name, e.g. `"Tony's"`. Copied into `transactions.merchantName`. |
| `city`     | string    | Display location, e.g. `"Greenwich, CT"`. |
| `logoRef`  | string    | Logo asset key bundled in both apps, e.g. `"merchant_tonys"`. |
| `offer`    | string    | Offer text shown on the merchant card. |
| `geofence` | map       | Where the "you're at Tony's" prompt fires. |
| `geofence.center` | GeoPoint | Latitude and longitude of the merchant. |
| `geofence.radiusMeters` | int | Radius of the geofence circle in metres. |

Seeded: `merchants/tonys-greenwich` — Tony's, Greenwich, CT, centre
`41.0262, -73.6282`, radius `150` m. The coordinates are an approximate point on
Greenwich Avenue. Adjust them in `seedData.js` once the demo spot is known.

---

## `parties/{partyId}`

One document per Split Party. The id is a Firestore auto id.

| Field               | Type                          | Description |
| ------------------- | ----------------------------- | ----------- |
| `hostId`            | string                        | `users` id of the host. The host is also a member. |
| `merchantId`        | string                        | `merchants` id where the party happens. |
| `joinCode`          | array\<string\>, length 3     | The three-emoji join code, one emoji per element, each from the **emoji palette** below. Repeats are allowed. Example: `["🍕","🍺","🎉"]`. |
| `status`            | string enum                   | `"open"`, `"paid"` or `"expired"`. See *Lifecycle*. |
| `createdAt`         | Timestamp                     | When the host created the party. **The client writes this value itself** (not `serverTimestamp()`) so that `expiresAt` can be computed in the same write. |
| `expiresAt`         | Timestamp (derived, stored)   | Always `createdAt + 5 minutes`. Written once at creation, never changed. |
| `paidAt`            | Timestamp \| null             | When the host tapped Pay. `null` until then. |
| `billCents`         | int \| null                   | Pre-tip bill in cents, entered by the host. `null` until entered, `>= 0` after. |
| `tip`               | map \| null                   | Tip choice. `null` until chosen. Exactly one of these two shapes: |
| `tip.type`          | string enum                   | `"percent"` or `"custom"`. |
| `tip.percent`       | int                           | Only when `type == "percent"`. One of the presets `15`, `18`, `20`, `25`. |
| `tip.amountCents`   | int                           | Only when `type == "custom"`. Tip in cents, `>= 0`. |
| `memberIds`         | array\<string\>               | User ids of everyone in the party, **host first**, then joinees in join order, 1–8 entries (PoC cap). Use it for `array-contains` queries and for headcount. Keep it in sync with `members` (same keys). |
| `members`           | map\<userId, map\>            | Per-member state, keyed by user id. |
| `members.{uid}.joinedAt` | Timestamp                | When this member joined (for the host: same as `createdAt`). |
| `members.{uid}.paid`     | bool                     | `false` on join. Set to `true` for every member in the Pay transaction. |
| `wrongCodeAttempts` | map\<userId, int\>            | Wrong join-code attempts per user against this party. Missing key means 0. A user with a value `>= 3` is **locked out**: the app must refuse further attempts and never add them to `members`. |

**Not stored (derived on each phone):** total, tip in cents for percent tips,
headcount and each member's share. Compute them with the split rule below.
Never write a `shareCents` or `totalCents` field.

### Emoji palette

Join codes use only these 12 single-code-point emojis (no skin tones, no
variation selectors, so every client compares strings the same way):

```
🍕 🍔 🌮 🍣 🍩 🍺 🎉 🔥 🚀 🎸 🐶 🌵
```

That gives 12³ = 1728 possible codes per merchant.

### Lifecycle

```
            host taps Pay                       
  open ─────────────────────────────▶ paid      
    │                                            
    │ now >= expiresAt (and not paid)            
    └───────────────────────────────▶ expired   
```

- `open`: created by the host. Joinees can join only while
  `status == "open"` and `now < expiresAt`.
- `paid`: terminal. Set by the host's Pay transaction.
- `expired`: terminal. There is no server job in the PoC. Any client that sees
  `status == "open"` with `now >= expiresAt` treats the party as expired, and
  the host's app writes `status: "expired"` and deletes the join-code lock (see
  below) in one batch. Clients must treat an `open` party past `expiresAt` as
  expired even if nobody has written it yet.
- A party never goes back to `open`.

### Join-code uniqueness (per merchant, among active parties)

Uniqueness is guaranteed by a lock document whose **id is the code itself**:

```
joinCodes/{merchantId}_{e1}{e2}{e3}       e.g.  joinCodes/tonys-greenwich_🍕🍺🎉
```

| Field       | Type      | Description |
| ----------- | --------- | ----------- |
| `merchantId`| string    | Same as in the id. |
| `joinCode`  | array\<string\> | Same three emojis as the party. |
| `partyId`   | string    | The party that holds the code. |
| `expiresAt` | Timestamp | Copy of the party's `expiresAt`. |

Creating a party is one Firestore **transaction**:

1. Pick a random code from the palette and read `joinCodes/{merchantId}_{code}`.
2. If that lock exists **and** its `expiresAt` is in the future, the code is
   taken. Pick another code and retry from step 1.
3. Otherwise (no lock, or a stale lock from an expired party) write the lock
   (`set`, overwriting any stale lock) **and** the new party document in the
   same transaction.

Firestore transactions abort if a document they read is changed before they
commit. So two hosts at the same merchant can never both get the same code
while it is active. When a party becomes `paid` or `expired`, delete its lock
in the same write. Stale locks are harmless because step 2 ignores them.

Joining by code: read `joinCodes/{merchantId}_{code}`. If it exists, its
`expiresAt` is in the future and the referenced party is `open`, that is the
party to join. Wrong attempts are counted on the party the joinee is trying to
join (the party whose host they picked nearby) in `wrongCodeAttempts.{uid}`,
using `FieldValue.increment(1)`.

### Split rule (equal split, integer cents)

All arithmetic is integer arithmetic. No floats anywhere.

```
tipCents   = tip.type == "custom"  ? tip.amountCents
                                   : floor((billCents * tip.percent + 50) / 100)   // round half up
totalCents = billCents + tipCents
n          = memberIds.length          // headcount, host included
base       = floor(totalCents / n)
leftover   = totalCents - base * n     // 0 <= leftover < n
share(host)    = base + leftover
share(joinee)  = base
```

So the shares always sum exactly to `totalCents`, and any leftover cents go to
the host. The reference implementation is `backend/lib/split.js` (covered by
`npm test`).

**Worked example.** 3 people (host Maxi, Annie, Christophe), bill `$100.00`
(`billCents = 10000`), tip 18 % (`tip = {type: "percent", percent: 18}`):

| Step        | Value |
| ----------- | ----- |
| tipCents    | floor((10000 × 18 + 50) / 100) = floor(180050 / 100) = **1800** |
| totalCents  | 10000 + 1800 = **11800** ($118.00) |
| base        | floor(11800 / 3) = **3933** |
| leftover    | 11800 − 3933 × 3 = 11800 − 11799 = **1** |
| Maxi (host) | 3933 + 1 = **3934** ($39.34) |
| Annie       | **3933** ($39.33) |
| Christophe  | **3933** ($39.33) |
| Sum         | 3934 + 3933 + 3933 = **11800** = totalCents ✓ |

### Paying a party (host taps Pay)

One Firestore transaction, run by the host's app:

1. Read the party. It must be `open` with `now < expiresAt`, and `billCents`
   and `tip` must be set.
2. Compute the shares with the split rule.
3. For each joinee `j` (every member except the host), create
   `transactions/{partyId}_{j}` with `payerId = j`, `payeeId = hostId`,
   `amountCents = share(j)`, `memo = "{merchant.name} Split Party"`,
   `merchantId`, `merchantName`, `partyId`, `source = "party"`. The
   deterministic id means a retried Pay can never charge twice.
4. `users/{j}.balanceCents -= share(j)` and
   `users/{host}.balanceCents += share(j)` (`FieldValue.increment`).
5. Party update: `status = "paid"`, `paidAt = now`, every `members.{uid}.paid = true`.
6. Delete `joinCodes/{merchantId}_{code}`.

The host pays the merchant the full total outside the app (mock). Only the
joinee → host repayments are recorded.

---

## `transactions/{transactionId}`

Every payment shown in the feed and on the Me tab. Seeded feed entries use
ids `seed_tx_01`… and party payments use `{partyId}_{payerId}`.

| Field           | Type            | Description |
| --------------- | --------------- | ----------- |
| `payerId`       | string          | `users` id of who paid. |
| `payeeId`       | string          | `users` id of who received. Never equal to `payerId`. |
| `participantIds`| array\<string\> | Derived, stored: always `[payerId, payeeId]`. It exists only so the Me tab can use one `array-contains` query. Write it together with `payerId`/`payeeId`. |
| `amountCents`   | int             | Amount in cents, `> 0`. |
| `memo`          | string          | Feed text, e.g. `"Tony's Split Party"`. 1–80 chars. |
| `merchantId`    | string \| null  | `merchants` id when the payment came from a merchant, else `null`. |
| `merchantName`  | string \| null  | Copy of `merchants.{merchantId}.name` at payment time, else `null`. |
| `partyId`       | string \| null  | Originating `parties` id for party payments, else `null`. |
| `createdAt`     | Timestamp       | When the payment happened. Party payments write `serverTimestamp()`. |
| `likesCount`    | int             | Feed likes counter (mock), `>= 0`. Change it with `FieldValue.increment`. |
| `commentsCount` | int             | Feed comments counter (mock), `>= 0`. |
| `source`        | string enum     | `"seed"` (written by the seed script) or `"party"` (written by a Pay). Reset removes everything that is not a seeded entry. |

### Queries

**Feed** (newest first, all users):

```
transactions  orderBy("createdAt", "desc")  limit(50)
```

Single-field index, no extra config needed.

**Me tab** (the current user `uid` as payer or payee, newest first):

```
transactions  where("participantIds", "array-contains", uid)  orderBy("createdAt", "desc")  limit(50)
```

This needs the composite index `participantIds (array-contains) + createdAt
(desc)`, declared in `backend/firestore.indexes.json`.

**Open parties at a merchant** (nearby hosts for a joinee):

```
parties  where("merchantId", "==", m)  where("status", "==", "open")  where("expiresAt", ">", now)
```

Declared in `firestore.indexes.json` as well.

---

## Validation

`backend/firestore.rules` enforces these shapes for client writes, and
`backend/lib/validate.js` applies the same checks in scripts and tests. A
party document is **invalid** (rejected) if, among other things:

- `billCents` is not an integer (e.g. `12.5`, `"1250"`) or is negative;
- `status` is not one of `open`, `paid`, `expired`;
- `joinCode` does not have exactly 3 elements, or an element is not in the
  emoji palette (so `["🍕","🍺"]` and `["🍕","🍺","🎉","🔥"]` are both rejected);
- `tip` has neither or both of `percent`/`amountCents`, or a percent outside
  the presets;
- `expiresAt != createdAt + 5 minutes`;
- `memberIds` does not start with `hostId`, or its entries differ from the
  `members` keys.

The PoC uses username-only login without Firebase Auth, so the rules validate
**shape**, not identity. The seed/reset scripts use the Admin SDK, which
bypasses rules.
