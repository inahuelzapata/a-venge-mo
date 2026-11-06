import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateParty, validateUser } from '../../lib/validate.js';
import { SEED_USERS } from '../../lib/seedData.js';

const t0 = new Date('2026-11-03T20:00:00Z');
const t5 = new Date(t0.getTime() + 5 * 60 * 1000);

export function goodParty(over = {}) {
  return {
    hostId: 'maxi', merchantId: 'tonys-greenwich', joinCode: ['🍕', '🍺'], status: 'open',
    createdAt: t0, expiresAt: t5, paidAt: null, billCents: 10000, tip: { type: 'percent', percent: 18 },
    memberIds: ['maxi', 'annie'],
    members: { maxi: { joinedAt: t0, paid: false }, annie: { joinedAt: t0, paid: false } },
    wrongCodeAttempts: { christophe: 1 },
    ...over,
  };
}

test('a schema-conforming party is valid', () => assert.deepEqual(validateParty(goodParty()), []));

test('a party cancelled by its host (no total, no joinees) is valid', () => assert.deepEqual(validateParty(goodParty({
  status: 'cancelled', cancelledAt: t0, cancelledBy: 'maxi', billCents: null, tip: null,
  memberIds: ['maxi'], members: { maxi: { joinedAt: t0, paid: false } },
})), []));

for (const [name, over] of [
  ['non-integer bill', { billCents: 12.5 }],
  ['string bill', { billCents: '1000' }],
  ['status outside the set', { status: 'closed' }],
  ['1-emoji code', { joinCode: ['🍕'] }],
  ['3-emoji code', { joinCode: ['🍕', '🍺', '🎉'] }],
  ['4-emoji code', { joinCode: ['🍕', '🍺', '🎉', '🔥'] }],
  ['emoji outside the palette', { joinCode: ['🍕', '😀'] }],
  ['expiry not +5 min', { expiresAt: new Date(t0.getTime() + 60000) }],
  ['host not first member', { memberIds: ['annie', 'maxi'] }],
  ['bad tip preset', { tip: { type: 'percent', percent: 17 } }],
  ['stored share field', { shareCents: 3933 }],
  ['cancelled without a cancel stamp', { status: 'cancelled' }],
  ['cancelled by a non-host', { status: 'cancelled', cancelledAt: t0, cancelledBy: 'annie' }],
  ['cancel stamp on an open party', { cancelledAt: t0, cancelledBy: 'maxi' }],
]) {
  test(`invalid: ${name}`, () => assert.notDeepEqual(validateParty(goodParty(over)), []));
}

// Custom shares (docs/firestore-schema.md "Custom shares").
const trio = (custom = {}, over = {}) => goodParty({
  billCents: 6000, tip: { type: 'custom', amountCents: 0 }, memberIds: ['maxi', 'annie', 'christophe'],
  members: Object.fromEntries(['maxi', 'annie', 'christophe'].map((id) => [id, { joinedAt: t0, paid: false, ...(id in custom ? { customShareCents: custom[id] } : {}) }])),
  ...over,
});

test('custom shares: absent, null for some members, or set for every member are all valid', () => {
  assert.deepEqual(validateParty(trio()), []);
  assert.deepEqual(validateParty(trio({ annie: null, maxi: 1000 })), []);
  assert.deepEqual(validateParty(trio({ maxi: 1000, annie: 3500, christophe: 1500 })), []);
  // An open party whose bill dropped under its amounts is kept (the host must fix them before Collect).
  assert.deepEqual(validateParty(trio({ annie: 3500, christophe: 3500 })), []);
});

for (const [name, party] of [
  ['non-integer custom amount', trio({ annie: 12.5 })],
  ['negative custom amount', trio({ annie: -1 })],
  ['string custom amount', trio({ annie: '3500' })],
  ['any other new member key', goodParty({ members: { maxi: { joinedAt: t0, paid: false, shareCents: 1 }, annie: { joinedAt: t0, paid: false } } })],
  ['paid with custom amounts over the total', trio({ annie: 3500, christophe: 3500 }, { status: 'paid', paidAt: t0 })],
  ['paid with every member custom not adding up', trio({ maxi: 1000, annie: 1000, christophe: 1000 }, { status: 'paid', paidAt: t0 })],
]) {
  test(`invalid: ${name}`, () => assert.notDeepEqual(validateParty(party), []));
}

test('seeded users carry their photo URL as avatar and pass the validator', () => {
  const base = 'https://taller.ai/_next/image?url=%2Fleadership%2F';
  assert.deepEqual(Object.fromEntries(Object.entries(SEED_USERS).map(([id, u]) => [id, u.avatar])), {
    maxi: `${base}maximiliano-armesto.jpg&w=1080&q=75`,
    annie: `${base}annie-rodriguez.jpg&w=1080&q=75`,
    christophe: `${base}christophe-kolb.jpg&w=1080&q=75`,
    ...Object.fromEntries(['jan', 'belen', 'ale', 'paloma', 'marcelo', 'pochi', 'nahuel', 'dami', 'coco', 'franco', 'guille'].map((id) =>
      [id, `https://raw.githubusercontent.com/inahuelzapata/a-venge-mo/main/avatars/${id}.jpg`])),
  });
  for (const [id, u] of Object.entries(SEED_USERS)) {
    const doc = { username: id, displayName: u.displayName, avatar: u.avatar, balanceCents: u.balanceCents,
      friendIds: Object.keys(SEED_USERS).filter((f) => f !== id) };
    assert.deepEqual(validateUser(id, doc), []);
  }
});
