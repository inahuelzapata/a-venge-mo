// The resetDemo callable, run on the Firestore + Functions emulators:
//   npm run test:functions
// Calls the function over the callable HTTP protocol, exactly as the apps' Demo
// reset button does (no auth, no arguments), and compares the whole database
// with the fresh-seed state and with what `npm run reset` produces.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue, GeoPoint, getFirestore, Timestamp } from 'firebase-admin/firestore';
import { BACKEND_DIR } from '../../lib/env.js';
import { RESET_DEMO_FUNCTION, RESET_DEMO_REGION } from '../../lib/deploy.js';
import { seed } from '../../lib/ops.js';
import { MERCHANT_ID, SEED_USERS } from '../../lib/seedData.js';
import { computeSplit } from '../../lib/split.js';
import { joinCodeLockId, partyTransactionId, PARTY_TTL_MS } from '../../lib/schema.js';

const COLLECTIONS = ['users', 'merchants', 'parties', 'joinCodes', 'transactions'];
const projectId = process.env.GCLOUD_PROJECT;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
// emulators:exec does not export the Functions emulator address; it is fixed in firebase.json.
const { host: fnHost, port: fnPort } = JSON.parse(readFileSync(join(BACKEND_DIR, 'firebase.json'), 'utf8')).emulators.functions;
const functionsHost = `${fnHost}:${fnPort}`;
let db;
let seededState;

async function wipe() {
  for (const c of COLLECTIONS) {
    const refs = await db.collection(c).listDocuments();
    await Promise.all(refs.map((r) => r.delete()));
  }
}

/** Every document in every collection, as comparable JSON. */
async function snapshot() {
  const out = {};
  for (const c of COLLECTIONS) {
    const s = await db.collection(c).get();
    out[c] = Object.fromEntries(
      s.docs.map((d) => [d.id, JSON.parse(JSON.stringify(d.data(), (_k, v) =>
        v instanceof Timestamp ? `ts:${v.toMillis()}` : v instanceof GeoPoint ? `geo:${v.latitude},${v.longitude}` : v))]),
    );
  }
  return out;
}

/** Calls resetDemo as a client would: POST {data:null}, no auth. Returns { status, body, ms }. */
async function callResetDemo() {
  const url = `http://${functionsHost}/${projectId}/${RESET_DEMO_REGION}/${RESET_DEMO_FUNCTION}`;
  const t0 = Date.now();
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ data: null }),
  });
  return { status: res.status, body: await res.json(), ms: Date.now() - t0 };
}

function assertSuccess({ status, body }) {
  assert.equal(status, 200, JSON.stringify(body));
  assert.equal(body.result?.ok, true);
  const at = Date.parse(body.result.resetAt);
  assert.ok(Number.isFinite(at) && Math.abs(Date.now() - at) < 60_000, `resetAt is a current ISO timestamp: ${body.result.resetAt}`);
}

/**
 * One finished party, as the apps leave it: 3 members, host paid for everyone
 * (2 party transactions, moved balances); its join-code lock is still present,
 * plus an open party with its own lock.
 */
async function runPartyToPaid() {
  const partyRef = db.collection('parties').doc();
  const createdAt = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(createdAt.toMillis() + PARTY_TTL_MS);
  const joinCode = ['🍕', '🍺', '🎉'];
  const lockRef = db.doc(`joinCodes/${joinCodeLockId(MERCHANT_ID, joinCode)}`);
  await lockRef.set({ merchantId: MERCHANT_ID, joinCode, partyId: partyRef.id, expiresAt });
  await partyRef.set({
    hostId: 'maxi', merchantId: MERCHANT_ID, joinCode, status: 'open', createdAt, expiresAt, paidAt: null,
    billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: ['maxi', 'annie', 'christophe'],
    members: Object.fromEntries(['maxi', 'annie', 'christophe'].map((u) => [u, { joinedAt: createdAt, paid: false }])),
    wrongCodeAttempts: {},
  });
  await db.runTransaction(async (tx) => {
    const p = (await tx.get(partyRef)).data();
    const { shares } = computeSplit(p);
    const update = { status: 'paid', paidAt: Timestamp.now() };
    for (const uid of p.memberIds) update[`members.${uid}.paid`] = true;
    for (const j of p.memberIds.slice(1)) {
      tx.set(db.doc(`transactions/${partyTransactionId(partyRef.id, j)}`), {
        payerId: j, payeeId: p.hostId, participantIds: [j, p.hostId], amountCents: shares[j],
        memo: "Tony's Split Party", merchantId: MERCHANT_ID, merchantName: "Tony's", partyId: partyRef.id,
        createdAt: FieldValue.serverTimestamp(), likesCount: 0, commentsCount: 0, source: 'party',
      });
      tx.update(db.doc(`users/${j}`), { balanceCents: FieldValue.increment(-shares[j]) });
      tx.update(db.doc(`users/${p.hostId}`), { balanceCents: FieldValue.increment(shares[j]) });
    }
    tx.update(partyRef, update);
  });
  const open = db.collection('parties').doc();
  const openCode = ['🌮', '🔥', '🚀'];
  await open.set({ ...(await partyRef.get()).data(), joinCode: openCode, status: 'open', paidAt: null });
  await db.doc(`joinCodes/${joinCodeLockId(MERCHANT_ID, openCode)}`)
    .set({ merchantId: MERCHANT_ID, joinCode: openCode, partyId: open.id, expiresAt });

  // the starting state the work order describes
  assert.equal((await db.collection('transactions').where('source', '==', 'party').get()).size, 2);
  assert.equal((await db.collection('joinCodes').get()).size, 2);
  assert.notEqual((await db.doc('users/maxi').get()).get('balanceCents'), SEED_USERS.maxi.balanceCents);
}

before(async () => {
  if (!projectId?.startsWith('demo-') || !firestoreHost || !process.env.FIREBASE_EMULATOR_HUB) {
    throw new Error('This suite wipes the database and needs both emulators. Run it with: npm run test:functions');
  }
  db = getFirestore(initializeApp({ projectId }));
  await wipe();
  await seed(db);
  seededState = await snapshot();
});

test('after a paid party, resetDemo returns success within 5 s and the DB equals the fresh seed', async () => {
  await runPartyToPaid();
  const call = await callResetDemo();
  assertSuccess(call);
  assert.ok(call.ms < 5000, `took ${call.ms} ms`);
  assert.equal((await db.collection('parties').get()).size, 0);
  assert.equal((await db.collection('joinCodes').get()).size, 0);
  const txIds = (await db.collection('transactions').get()).docs.map((d) => d.id).sort();
  assert.deepEqual(txIds, Object.keys(seededState.transactions).sort());
  for (const [uid, u] of Object.entries(SEED_USERS)) {
    assert.equal((await db.doc(`users/${uid}`).get()).get('balanceCents'), u.balanceCents);
  }
  assert.deepEqual(await snapshot(), seededState);
});

test('resetDemo on an already-seeded DB succeeds and changes nothing', async () => {
  assert.deepEqual(await snapshot(), seededState);
  assertSuccess(await callResetDemo());
  assert.deepEqual(await snapshot(), seededState);
});

test('two resetDemo calls at the same time both succeed and leave one seeded state', async () => {
  await runPartyToPaid();
  const calls = await Promise.all([callResetDemo(), callResetDemo()]);
  for (const c of calls) assertSuccess(c);
  assert.deepEqual(await snapshot(), seededState);
});

test('npm run reset and resetDemo give identical databases from the same starting state', async () => {
  await runPartyToPaid();
  const env = {
    PATH: process.env.PATH, HOME: process.env.HOME,
    FIREBASE_TARGET: 'emulator', FIREBASE_PROJECT_ID: projectId, FIRESTORE_EMULATOR_HOST: firestoreHost,
  };
  const r = spawnSync(process.execPath, ['scripts/reset.js'], { cwd: BACKEND_DIR, env, encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /Removed: 2 parties, 2 join-code locks, 2 non-seed transactions/);
  const afterCli = await snapshot();

  await runPartyToPaid();
  assertSuccess(await callResetDemo());
  assert.deepEqual(await snapshot(), afterCli);
  assert.deepEqual(afterCli, seededState);
});
