// firestore.rules accepts schema-conforming parties and rejects the invalid
// shapes listed in docs/firestore-schema.md ("Validation").
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, deleteField, doc, getDoc, getDocs, collection, runTransaction, setDoc, updateDoc, Timestamp } from 'firebase/firestore';

let env;
let db;
const created = Timestamp.fromMillis(Date.UTC(2026, 10, 3, 20, 0, 0));
const expires = Timestamp.fromMillis(created.toMillis() + 5 * 60 * 1000);

function party(over = {}) {
  return {
    hostId: 'maxi', merchantId: 'tonys-greenwich', joinCode: ['🍕', '🍺'], status: 'open',
    createdAt: created, expiresAt: expires, paidAt: null, billCents: 10000, tip: { type: 'percent', percent: 18 },
    memberIds: ['maxi', 'annie'],
    members: { maxi: { joinedAt: created, paid: false }, annie: { joinedAt: created, paid: false } },
    wrongCodeAttempts: {},
    ...over,
  };
}

before(async () => {
  // @firebase/rules-unit-testing only works against the emulator: it loads the
  // rules file into it and clears data between runs. Never let it near hosted.
  if (process.env.FIREBASE_TARGET === 'hosted' || !process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      'The security-rules suite runs only under the Firestore emulator (FIREBASE_TARGET=hosted or no emulator ' +
        'running). Use: npm run test:rules. Hosted rules are checked by npm run test:hosted -- --wipe-demo-db.',
    );
  }
  env = await initializeTestEnvironment({
    projectId: 'demo-split-party-rules', // separate project so it never touches the acceptance data
    firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
  });
  await env.clearFirestore();
  db = env.unauthenticatedContext().firestore();
});
after(async () => env?.cleanup());

test('accepts a schema-conforming party', () => assertSucceeds(setDoc(doc(db, 'parties/ok'), party())));

for (const [name, over] of [
  ['non-integer bill', { billCents: 12.5 }],
  ['string bill', { billCents: '10000' }],
  ['status outside the allowed set', { status: 'closed' }],
  ['join code with 1 emoji', { joinCode: ['🍕'] }],
  ['join code with 3 emojis', { joinCode: ['🍕', '🍺', '🎉'] }],
  ['join code with 4 emojis', { joinCode: ['🍕', '🍺', '🎉', '🔥'] }],
  ['emoji outside the palette', { joinCode: ['🍕', '😀'] }],
  ['expiresAt not createdAt + 5 min', { expiresAt: Timestamp.fromMillis(created.toMillis() + 60000) }],
  ['stored share field', { shareCents: 3933 }],
  ['tip percent outside presets', { tip: { type: 'percent', percent: 17 } }],
]) {
  test(`rejects party with ${name}`, () => assertFails(setDoc(doc(db, `parties/bad-${name.replace(/\W+/g, '-')}`), party(over))));
}

test('accepts a valid paid update, rejects reopening a paid party', async () => {
  await assertSucceeds(updateDoc(doc(db, 'parties/ok'), {
    status: 'paid', paidAt: Timestamp.now(), 'members.maxi.paid': true, 'members.annie.paid': true,
  }));
  await assertFails(updateDoc(doc(db, 'parties/ok'), { status: 'open' }));
});

// The writes the apps' total slice makes (Android data/FirestorePartyRepository.kt):
// bill (+ the 0% tip as custom 0 while none is stored), tip changes, host
// removal and leaving, each one update of only those fields.
test('accepts the total slice writes: bill, tips, removal and leave', async () => {
  const ref = doc(db, 'parties/total');
  await assertSucceeds(setDoc(ref, party({
    billCents: null, tip: null, memberIds: ['maxi', 'annie', 'christophe'],
    members: { maxi: { joinedAt: created, paid: false }, annie: { joinedAt: created, paid: false },
      christophe: { joinedAt: created, paid: false } },
  })));
  await assertSucceeds(updateDoc(ref, { billCents: 4850, tip: { type: 'custom', amountCents: 0 } }));
  await assertSucceeds(updateDoc(ref, { billCents: 10000 }));
  await assertSucceeds(updateDoc(ref, { tip: { type: 'percent', percent: 18 } }));
  await assertSucceeds(updateDoc(ref, { tip: { type: 'custom', amountCents: 700 } }));
  await assertSucceeds(updateDoc(ref, { memberIds: ['maxi', 'annie'], 'members.christophe': deleteField() }));
  await assertSucceeds(updateDoc(ref, { memberIds: ['maxi'], 'members.annie': deleteField() }));
});

test('rejects a 0 percent tip and a removal that leaves members out of sync', async () => {
  const ref = doc(db, 'parties/total-bad');
  await assertSucceeds(setDoc(ref, party()));
  await assertFails(updateDoc(ref, { tip: { type: 'percent', percent: 0 } }));
  await assertFails(updateDoc(ref, { memberIds: ['maxi'] }));
  await assertFails(updateDoc(ref, { memberIds: ['annie'], 'members.maxi': deleteField() }));
});

test('a user document whose avatar is a photo URL: phone may move the balance, not the avatar', async () => {
  const avatar = 'https://taller.ai/_next/image?url=%2Fleadership%2Fmaximiliano-armesto.jpg&w=1080&q=75';
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'users/maxi'), {
    username: 'maxi', displayName: 'Maxi', avatar, balanceCents: 25000, friendIds: ['annie', 'christophe'],
  }));
  await assertSucceeds(updateDoc(doc(db, 'users/maxi'), { balanceCents: 20000 }));
  await assertFails(updateDoc(doc(db, 'users/maxi'), { avatar: 'avatar_maxi' }));
});

// joinCodes/{merchantId}_{e1}{e2}: the lock that keeps a code unique per merchant.
function lock(code, over = {}) {
  return { merchantId: 'tonys-greenwich', joinCode: code, partyId: 'p1', expiresAt: expires, ...over };
}

test('accepts a two-emoji lock keyed {merchantId}_{e1}{e2}, and its delete', async () => {
  await assertSucceeds(setDoc(doc(db, 'joinCodes/tonys-greenwich_🍕🍺'), lock(['🍕', '🍺'])));
  await assertSucceeds(setDoc(doc(db, 'joinCodes/tonys-greenwich_🍕🍕'), lock(['🍕', '🍕'])));
  await assertSucceeds(deleteDoc(doc(db, 'joinCodes/tonys-greenwich_🍕🍕')));
});

test('rejects a lock with a three-emoji or one-emoji code, or an id that does not match its code', async () => {
  await assertFails(setDoc(doc(db, 'joinCodes/tonys-greenwich_🍕🍺🎉'), lock(['🍕', '🍺', '🎉'])));
  await assertFails(setDoc(doc(db, 'joinCodes/tonys-greenwich_🍕'), lock(['🍕'])));
  await assertFails(setDoc(doc(db, 'joinCodes/tonys-greenwich_🍕🎉'), lock(['🍕', '🍺'])));
});

// Two hosts at the same merchant start parties at the same moment, both drawing
// the same first code. The create transaction (as in the apps) reserves the lock
// with the party, retrying the next code when the lock is held by a live party.
test('concurrent creates at one merchant never hold the same two-emoji code', async () => {
  const merchantId = 'race-merchant';
  const candidates = [['🍕', '🍺'], ['🌮', '🔥'], ['🚀', '🎸'], ['🐶', '🌵']];
  const create = (hostId) => runTransaction(db, async (tx) => {
    const partyRef = doc(collection(db, 'parties'));
    for (const code of candidates) {
      const lockRef = doc(db, `joinCodes/${merchantId}_${code.join('')}`);
      const held = await tx.get(lockRef);
      if (held.exists() && held.get('expiresAt').toMillis() > Date.now()) continue;
      const createdAt = Timestamp.fromMillis(Math.floor(Date.now() / 1000) * 1000);
      const expiresAt = Timestamp.fromMillis(createdAt.toMillis() + 5 * 60 * 1000);
      tx.set(lockRef, { merchantId, joinCode: code, partyId: partyRef.id, expiresAt });
      tx.set(partyRef, party({
        hostId, merchantId, joinCode: code, createdAt, expiresAt, billCents: null, tip: null,
        memberIds: [hostId], members: { [hostId]: { joinedAt: createdAt, paid: false } }, wrongCodeAttempts: {},
      }));
      return code.join('');
    }
    throw new Error('no free code');
  });
  const codes = await Promise.all([create('maxi'), create('annie'), create('christophe')]);
  assert.equal(new Set(codes).size, 3, `distinct codes, got ${codes}`);
  const locks = await getDocs(collection(db, 'joinCodes'));
  const raceLocks = locks.docs.filter((d) => d.id.startsWith(`${merchantId}_`));
  assert.equal(raceLocks.length, 3);
  for (const d of raceLocks) {
    assert.equal(d.id, `${merchantId}_${d.get('joinCode').join('')}`);
    assert.equal(d.get('joinCode').length, 2);
    assert.ok((await getDoc(doc(db, `parties/${d.get('partyId')}`))).exists());
  }
});
