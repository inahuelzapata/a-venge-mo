// Work-order acceptance checks, run against the database in backend/.env:
// the HOSTED demo database by default (npm run test:hosted -- --wipe-demo-db).
// The runner deploys rules + indexes, resets before and after, so this file may
// wipe freely. It refuses to run unless that runner started it.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { FieldValue, Timestamp, GeoPoint } from 'firebase-admin/firestore';
import { BACKEND_DIR, openFirestore, readConfig } from '../../lib/env.js';
import { seed, reset } from '../../lib/ops.js';
import { buildSeed, MERCHANT_ID } from '../../lib/seedData.js';
import { computeSplit } from '../../lib/split.js';
import { joinCodeLockId, partyTransactionId, PARTY_TTL_MS } from '../../lib/schema.js';
import { validateParty, validateTransaction, validateUser } from '../../lib/validate.js';

const firebaseDir = BACKEND_DIR;
let config;
let db;

async function wipe() {
  for (const c of ['users', 'merchants', 'parties', 'joinCodes', 'transactions']) {
    const refs = await db.collection(c).listDocuments();
    await Promise.all(refs.map((r) => r.delete()));
  }
}

/** Every document in every collection, as comparable JSON. */
async function snapshot() {
  const out = {};
  for (const c of ['users', 'merchants', 'parties', 'joinCodes', 'transactions']) {
    const s = await db.collection(c).get();
    out[c] = Object.fromEntries(
      s.docs.map((d) => [d.id, JSON.parse(JSON.stringify(d.data(), (_k, v) =>
        v instanceof Timestamp ? `ts:${v.toMillis()}` : v instanceof GeoPoint ? `geo:${v.latitude},${v.longitude}` : v))]),
    );
  }
  return out;
}

before(async () => {
  if (process.env.SPLIT_PARTY_ACCEPTANCE_RUNNER !== '1') {
    throw new Error('This suite wipes the database. Run it through: npm run test:hosted -- --wipe-demo-db');
  }
  config = readConfig();
  db = await openFirestore(config);
  await wipe();
});

let afterFirstSeed;

test('seed on an empty DB: 3 mutual-friend users with name, avatar, non-zero balance', async () => {
  assert.equal((await db.collection('users').get()).size, 0);
  await seed(db);
  const users = await db.collection('users').get();
  assert.equal(users.size, 3);
  const ids = users.docs.map((d) => d.id).sort();
  assert.deepEqual(ids, ['annie', 'christophe', 'maxi']);
  for (const d of users.docs) {
    const u = d.data();
    assert.deepEqual(validateUser(d.id, u), []);
    assert.deepEqual([...u.friendIds].sort(), ids.filter((i) => i !== d.id));
    assert.ok(u.displayName && u.avatar);
    assert.ok(Number.isInteger(u.balanceCents) && u.balanceCents !== 0);
  }
  afterFirstSeed = await snapshot();
});

test('seed twice: identical counts and values, no duplicates', async () => {
  await seed(db);
  assert.deepEqual(await snapshot(), afterFirstSeed);
});

test("after seed: Tony's merchant has name, logo, offer, coordinates; zero open parties", async () => {
  const m = (await db.doc(`merchants/${MERCHANT_ID}`).get()).data();
  assert.equal(m.name, "Tony's");
  assert.ok(m.logoRef && m.offer);
  assert.ok(m.geofence.center instanceof GeoPoint && m.geofence.radiusMeters > 0);
  assert.equal((await db.collection('merchants').get()).size, 1);
  assert.equal((await db.collection('parties').where('status', '==', 'open').get()).size, 0);
});

test('after seed: feed entries are complete and the newest-first query orders them', async () => {
  const feed = await db.collection('transactions').orderBy('createdAt', 'desc').limit(50).get();
  const expected = buildSeed({ Timestamp, GeoPoint }).transactions;
  assert.equal(feed.size, Object.keys(expected).length);
  const times = feed.docs.map((d) => d.get('createdAt').toMillis());
  assert.deepEqual(times, [...times].sort((a, b) => b - a));
  for (const d of feed.docs) {
    const t = d.data();
    assert.deepEqual(validateTransaction(t), []);
    assert.ok(t.payerId && t.payeeId && Number.isInteger(t.amountCents) && t.memo && t.createdAt);
  }
  // Me-tab query (needs the composite index in production)
  const me = await db.collection('transactions').where('participantIds', 'array-contains', 'maxi')
    .orderBy('createdAt', 'desc').get();
  assert.ok(me.docs.every((d) => d.get('payerId') === 'maxi' || d.get('payeeId') === 'maxi'));
  assert.equal(me.size, Object.values(expected).filter((t) => t.participantIds.includes('maxi')).length);
});

test('a full party (create, join, pay) then reset restores the fresh-seed state', async () => {
  // --- create (documented transaction: lock + party) ---
  const partyRef = db.collection('parties').doc();
  const createdAt = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(createdAt.toMillis() + PARTY_TTL_MS);
  const joinCode = ['🍕', '🍺', '🎉'];
  const lockRef = db.doc(`joinCodes/${joinCodeLockId(MERCHANT_ID, joinCode)}`);
  await db.runTransaction(async (tx) => {
    const lock = await tx.get(lockRef);
    assert.ok(!lock.exists || lock.get('expiresAt').toMillis() <= Date.now(), 'code free');
    tx.set(lockRef, { merchantId: MERCHANT_ID, joinCode, partyId: partyRef.id, expiresAt });
    tx.set(partyRef, {
      hostId: 'maxi', merchantId: MERCHANT_ID, joinCode, status: 'open', createdAt, expiresAt, paidAt: null,
      billCents: null, tip: null, memberIds: ['maxi'], members: { maxi: { joinedAt: createdAt, paid: false } },
      wrongCodeAttempts: {},
    });
  });

  // --- a wrong attempt, then two joins, then bill + tip ---
  await partyRef.update({ 'wrongCodeAttempts.christophe': FieldValue.increment(1) });
  for (const uid of ['annie', 'christophe']) {
    await partyRef.update({
      memberIds: FieldValue.arrayUnion(uid),
      [`members.${uid}`]: { joinedAt: Timestamp.now(), paid: false },
    });
  }
  await partyRef.update({ billCents: 10000, tip: { type: 'percent', percent: 18 } });
  assert.deepEqual(validateParty((await partyRef.get()).data()), []);

  // --- pay (documented transaction) ---
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
    tx.delete(lockRef);
  });
  const paid = (await partyRef.get()).data();
  assert.deepEqual(validateParty(paid), []);
  assert.ok(Object.values(paid.members).every((m) => m.paid));
  assert.equal((await db.doc('users/annie').get()).get('balanceCents'), 18050 - 3933);
  assert.equal((await db.doc('users/maxi').get()).get('balanceCents'), 25000 + 3933 * 2);
  assert.equal((await db.collection('transactions').where('partyId', '==', partyRef.id).get()).size, 2);

  // also leave an open party behind with a lock
  const open = db.collection('parties').doc();
  await open.set({ ...paid, status: 'open', paidAt: null });

  // --- reset ---
  await reset(db);
  assert.equal((await db.collection('parties').get()).size, 0);
  assert.equal((await db.collection('joinCodes').get()).size, 0);
  const txs = await db.collection('transactions').get();
  assert.ok(txs.docs.every((d) => d.get('source') === 'seed'));
  assert.deepEqual(await snapshot(), afterFirstSeed);
});

for (const script of ['seed', 'reset']) {
  test(`${script} without env fails with a clear message and writes nothing`, async () => {
    const before = await snapshot();
    const env = { PATH: process.env.PATH, HOME: process.env.HOME }; // no FIREBASE_* / emulator host
    const r = spawnSync(process.execPath, [`scripts/${script}.js`], { cwd: firebaseDir, env, encoding: 'utf8' });
    assert.equal(r.status, 2);
    assert.match(r.stderr, /FIREBASE_TARGET is not set/);
    assert.match(r.stderr, /FIREBASE_PROJECT_ID is not set/);
    assert.match(r.stderr, /Nothing was written/);
    assert.deepEqual(await snapshot(), before);
  });
}

// ---- Deployed security rules, exercised as a phone would (no credentials) ----

/** Firestore REST typed value for a plain JS value (Date -> timestamp). */
function enc(v) {
  if (v === null) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === 'string') return { stringValue: v };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(enc) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)])) } };
}

/** Creates parties/{id} with an unauthenticated client request; returns the HTTP status. */
async function clientCreateParty(id, data) {
  const base = config.target === 'emulator'
    ? `http://${config.emulatorHost}/v1`
    : 'https://firestore.googleapis.com/v1';
  const url = `${base}/projects/${config.projectId}/databases/(default)/documents/parties?documentId=${id}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ fields: enc(data).mapValue.fields }),
  });
  return res.status;
}

function clientParty(over = {}) {
  const created = new Date(Math.floor(Date.now() / 1000) * 1000);
  return {
    hostId: 'maxi', merchantId: MERCHANT_ID, joinCode: ['🍕', '🍺', '🎉'], status: 'open',
    createdAt: created, expiresAt: new Date(created.getTime() + PARTY_TTL_MS), paidAt: null,
    billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: ['maxi', 'annie'],
    members: { maxi: { joinedAt: created, paid: false }, annie: { joinedAt: created, paid: false } },
    wrongCodeAttempts: {},
    ...over,
  };
}

test('deployed rules: a client may create a schema-conforming party', async () => {
  assert.equal(await clientCreateParty(`acceptance-ok-${Date.now()}`, clientParty()), 200);
});

for (const [name, over] of [
  ['non-integer bill', { billCents: 12.5 }],
  ['status outside the allowed set', { status: 'closed' }],
  ['join code with 2 emojis', { joinCode: ['🍕', '🍺'] }],
  ['emoji outside the palette', { joinCode: ['🍕', '🍺', '😀'] }],
  ['stored share field', { shareCents: 3933 }],
]) {
  test(`deployed rules: a client party with ${name} is rejected`, async () => {
    assert.equal(await clientCreateParty(`acceptance-bad-${Date.now()}`, clientParty(over)), 403);
  });
}
