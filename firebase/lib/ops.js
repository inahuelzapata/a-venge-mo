// Seed and reset operations, shared by the CLI scripts and the emulator tests.
import { GeoPoint, Timestamp } from 'firebase-admin/firestore';
import { COLLECTIONS } from './schema.js';
import { buildSeed } from './seedData.js';

const BATCH_LIMIT = 450; // below Firestore's 500 writes per batch

async function commitInChunks(db, writes) {
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const batch = db.batch();
    for (const w of writes.slice(i, i + BATCH_LIMIT)) w(batch);
    await batch.commit();
  }
}

/**
 * Upserts every seed document with set() (full overwrite) on its fixed id.
 * Running it any number of times gives the same documents. It never deletes;
 * reset is what removes non-seed data.
 */
export async function seed(db) {
  const data = buildSeed({ Timestamp, GeoPoint });
  const writes = [];
  for (const name of ['users', 'merchants', 'transactions']) {
    for (const [id, doc] of Object.entries(data[name])) {
      writes.push((b) => b.set(db.collection(COLLECTIONS[name]).doc(id), doc));
    }
  }
  await commitInChunks(db, writes);
  return {
    users: Object.keys(data.users).length,
    merchants: Object.keys(data.merchants).length,
    transactions: Object.keys(data.transactions).length,
  };
}

/**
 * Returns the database to exactly the freshly-seeded state, in one command:
 * deletes every party and join-code lock, every transaction, user and merchant
 * that is not part of the seed, then re-seeds (which restores balances, friends
 * and the seeded feed values).
 */
export async function reset(db) {
  const seedIds = buildSeed({ Timestamp, GeoPoint });
  const removed = { parties: 0, joinCodes: 0, transactions: 0, users: 0, merchants: 0 };
  const writes = [];

  for (const name of ['parties', 'joinCodes']) {
    const refs = await db.collection(COLLECTIONS[name]).listDocuments();
    removed[name] = refs.length;
    for (const ref of refs) writes.push((b) => b.delete(ref));
  }
  for (const name of ['transactions', 'users', 'merchants']) {
    const keep = new Set(Object.keys(seedIds[name]));
    const refs = (await db.collection(COLLECTIONS[name]).listDocuments()).filter((r) => !keep.has(r.id));
    removed[name] = refs.length;
    for (const ref of refs) writes.push((b) => b.delete(ref));
  }
  await commitInChunks(db, writes);
  const seeded = await seed(db);
  return { removed, seeded };
}
