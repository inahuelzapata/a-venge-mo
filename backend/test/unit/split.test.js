import { test } from 'node:test';
import assert from 'node:assert/strict';
import { computeSplit, tipCents } from '../../lib/split.js';

test('worked example from the schema doc: $100 + 18% across 3, leftover cent to host', () => {
  const r = computeSplit({ billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: ['maxi', 'annie', 'christophe'] });
  assert.equal(r.tipCents, 1800);
  assert.equal(r.totalCents, 11800);
  assert.deepEqual(r.shares, { maxi: 3934, annie: 3933, christophe: 3933 });
  assert.equal(Object.values(r.shares).reduce((a, b) => a + b, 0), r.totalCents);
});

test('percent tip rounds half up in integer cents', () => {
  assert.equal(tipCents(8475, { type: 'percent', percent: 18 }), 1526); // 1525.5 -> 1526
  assert.equal(tipCents(8472, { type: 'percent', percent: 18 }), 1525); // 1524.96 -> 1525
  assert.equal(tipCents(1234, { type: 'custom', amountCents: 500 }), 500);
});

test('shares always sum to the total and only the host gets leftover', () => {
  for (let bill = 0; bill < 3000; bill += 7) {
    for (let n = 1; n <= 8; n++) {
      const ids = Array.from({ length: n }, (_, i) => `u${i}`);
      const r = computeSplit({ billCents: bill, tip: { type: 'percent', percent: 20 }, memberIds: ids });
      const shares = ids.map((id) => r.shares[id]);
      assert.equal(shares.reduce((a, b) => a + b, 0), r.totalCents);
      assert.ok(shares.slice(1).every((s) => s === r.base));
      assert.ok(shares[0] - r.base < n);
    }
  }
});

test('rejects non-integer money', () => {
  assert.throws(() => computeSplit({ billCents: 12.5, tip: { type: 'custom', amountCents: 0 }, memberIds: ['a'] }));
});
