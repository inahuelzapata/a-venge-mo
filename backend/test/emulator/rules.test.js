// firestore.rules accepts schema-conforming parties and rejects the invalid
// shapes listed in docs/firestore-schema.md ("Validation").
import { test, before, after } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, updateDoc, Timestamp } from 'firebase/firestore';

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
