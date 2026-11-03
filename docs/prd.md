# Split Party — Product Requirements (DRAFT)

> **Draft placeholder.** This page summarises what the repository already
> commits to so that the shared docs area is complete. Replace it with the
> real Split Party PRD when it is available; keep the file name so existing
> links keep working.

## Problem

Splitting a restaurant bill among friends is slow: someone pays, then chases
everyone for their share. Split Party lets a group settle up at the table in
one tap.

## Product (PoC scope)

A Venmo-like mobile app (native iOS and native Android) with a mock wallet,
a social feed of payments, and one new feature: the **Split Party**.

1. **Login** with a username only (no password). Seeded accounts: Maxi, Annie
   and Christophe, who are all mutual friends.
2. **Feed and Me tab**: the latest payments across friends, and the payments
   the signed-in user took part in.
3. **Split Party at a partner merchant** (Tony's, Greenwich CT):
   - The host starts a party while inside the merchant's geofence. The app
     shows a three-emoji join code; the party expires after 5 minutes.
   - Friends join by entering the code. Three wrong attempts lock a user out
     of that party.
   - The host enters the bill and picks a tip (15/18/20/25 % or a custom
     amount). Every phone shows the same equal split; leftover cents go to
     the host.
   - The host taps **Pay**: each joinee pays the host their share, balances
     update, and the payments appear in the feed.

## Demo

Three physical phones (a mix of iPhone and Android), one per seeded user,
all pointed at the same Firestore database. See the
[demo runbook](demo-runbook.md).

## Out of scope for the PoC

Real money movement, authentication beyond username, server-side jobs
(expiry is enforced by the clients), more than 8 people per party, and CI.

## References

- Data contract: [Firestore schema](firestore-schema.md)
- How the repository is organised: [Repository structure](repo-structure.md)
