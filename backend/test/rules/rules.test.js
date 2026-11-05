// firestore.rules accepts schema-conforming parties and rejects the invalid
// shapes listed in docs/firestore-schema.md ("Validation").
import { test, before, after } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteField, doc, setDoc, updateDoc, Timestamp } from 'firebase/firestore';

let env;
let db;
const created = Timestamp.fromMillis(Date.UTC(2026, 10, 3, 20, 0, 0));
const expires = Timestamp.fromMillis(created.toMillis() + 5 * 60 * 1000);

function party(over = {}) {
  return {
    hostId: 'maxi', merchantId: 'tonys-greenwich', joinCode: ['🍕', '🍺', '🎉'], status: 'open',
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
  ['join code with 2 emojis', { joinCode: ['🍕', '🍺'] }],
  ['join code with 4 emojis', { joinCode: ['🍕', '🍺', '🎉', '🔥'] }],
  ['emoji outside the palette', { joinCode: ['🍕', '🍺', '😀'] }],
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
