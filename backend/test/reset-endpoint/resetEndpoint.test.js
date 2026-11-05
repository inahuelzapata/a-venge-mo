// The free reset endpoint (reset-endpoint/api/resetDemo.js) on the Firestore
// emulator:   npm run test:reset-endpoint
// Serves the real Vercel handler over HTTP and calls it as the apps do
// (POST {data:null}, no auth): the database must end equal to a fresh seed.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import handler from '../../reset-endpoint/api/resetDemo.js';
import { seed } from '../../lib/ops.js';
import { MERCHANT_ID, SEED_USERS } from '../../lib/seedData.js';
import { COLLECTIONS } from '../../lib/schema.js';

const projectId = process.env.GCLOUD_PROJECT;
let db;
let server;
let url;
let seededState;

async function snapshot() {
  const out = {};
  for (const c of Object.values(COLLECTIONS)) {
    const s = await db.collection(c).get();
    out[c] = Object.fromEntries(s.docs.map((d) => [d.id, JSON.stringify(d.data())]));
  }
  return out;
}

async function call(method = 'POST') {
  const res = await fetch(url, {
    method,
    headers: method === 'POST' ? { 'content-type': 'application/json' } : {},
    body: method === 'POST' ? JSON.stringify({ data: null }) : undefined,
  });
  return { status: res.status, body: await res.json() };
}

before(async () => {
  if (!projectId?.startsWith('demo-') || !process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error('This suite wipes the database and needs the Firestore emulator. Run: npm run test:reset-endpoint');
  }
  db = getFirestore(initializeApp({ projectId }));
  for (const c of Object.values(COLLECTIONS)) {
    await Promise.all((await db.collection(c).listDocuments()).map((r) => r.delete()));
  }
  await seed(db);
  seededState = await snapshot();
  server = createServer((req, res) => handler(req, res));
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  url = `http://127.0.0.1:${server.address().port}/api/resetDemo`;
});

after(() => server?.close());

test('after a party and a moved balance, a POST restores the fresh seed', async () => {
  const party = db.collection(COLLECTIONS.parties).doc();
  await party.set({ hostId: 'annie', merchantId: MERCHANT_ID, status: 'paid', createdAt: Timestamp.now() });
  await db.collection(COLLECTIONS.joinCodes).doc('lock').set({ partyId: party.id });
  await db.collection(COLLECTIONS.transactions).doc(`${party.id}_maxi`).set({ payerId: 'maxi', amountCents: 5900, source: 'party' });
  await db.doc(`${COLLECTIONS.users}/maxi`).update({ balanceCents: 1 });

  const { status, body } = await call();
  assert.equal(status, 200, JSON.stringify(body));
  assert.equal(body.result.ok, true);
  assert.equal(body.result.removed.parties, 1);
  assert.equal(body.result.removed.transactions, 1);
  assert.equal((await db.doc(`${COLLECTIONS.users}/maxi`).get()).get('balanceCents'), SEED_USERS.maxi.balanceCents);
  assert.deepEqual(await snapshot(), seededState);
});

test('a second POST on a reset database succeeds and changes nothing', async () => {
  const { status, body } = await call();
  assert.equal(status, 200);
  assert.equal(body.result.ok, true);
  assert.deepEqual(await snapshot(), seededState);
});

test('a GET is a safe probe: 400, nothing reset', async () => {
  await db.collection(COLLECTIONS.parties).doc('still-here').set({ status: 'open' });
  const { status, body } = await call('GET');
  assert.equal(status, 400);
  assert.equal(body.error.status, 'INVALID_ARGUMENT');
  assert.ok((await db.doc(`${COLLECTIONS.parties}/still-here`).get()).exists);
  await db.doc(`${COLLECTIONS.parties}/still-here`).delete();
});
