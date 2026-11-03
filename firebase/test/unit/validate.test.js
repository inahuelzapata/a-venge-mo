import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateParty } from '../../lib/validate.js';

const t0 = new Date('2026-11-03T20:00:00Z');
const t5 = new Date(t0.getTime() + 5 * 60 * 1000);

export function goodParty(over = {}) {
  return {
    hostId: 'maxi', merchantId: 'tonys-greenwich', joinCode: ['🍕', '🍺', '🎉'], status: 'open',
    createdAt: t0, expiresAt: t5, paidAt: null, billCents: 10000, tip: { type: 'percent', percent: 18 },
    memberIds: ['maxi', 'annie'],
    members: { maxi: { joinedAt: t0, paid: false }, annie: { joinedAt: t0, paid: false } },
    wrongCodeAttempts: { christophe: 1 },
    ...over,
  };
}

test('a schema-conforming party is valid', () => assert.deepEqual(validateParty(goodParty()), []));

for (const [name, over] of [
  ['non-integer bill', { billCents: 12.5 }],
  ['string bill', { billCents: '1000' }],
  ['status outside the set', { status: 'closed' }],
  ['2-emoji code', { joinCode: ['🍕', '🍺'] }],
  ['4-emoji code', { joinCode: ['🍕', '🍺', '🎉', '🔥'] }],
  ['emoji outside the palette', { joinCode: ['🍕', '🍺', '😀'] }],
  ['expiry not +5 min', { expiresAt: new Date(t0.getTime() + 60000) }],
  ['host not first member', { memberIds: ['annie', 'maxi'] }],
  ['bad tip preset', { tip: { type: 'percent', percent: 17 } }],
  ['stored share field', { shareCents: 3933 }],
]) {
  test(`invalid: ${name}`, () => assert.notDeepEqual(validateParty(goodParty(over)), []));
}
