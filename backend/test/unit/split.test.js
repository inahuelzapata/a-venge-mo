import { test } from 'node:test';
import assert from 'node:assert/strict';
import { computeSplit, customSharesOf, customSharesProblem, tipCents } from '../../lib/split.js';

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

// Custom shares (docs/firestore-schema.md, "Custom shares"): the host sets
// members.{uid}.customShareCents; everyone else splits what is left.
const trio = ['maxi', 'annie', 'christophe'];
const custom60 = { billCents: 6000, tip: { type: 'custom', amountCents: 0 }, memberIds: trio };
const sum = (shares) => Object.values(shares).reduce((a, b) => a + b, 0);

test('no custom amounts: unchanged equal split', () => {
  const plain = computeSplit({ billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: trio });
  const viaMembers = computeSplit({
    billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: trio,
    members: { maxi: { paid: false }, annie: { paid: false, customShareCents: null }, christophe: { paid: false } },
  });
  assert.deepEqual(viaMembers.shares, plain.shares);
  assert.deepEqual(viaMembers.custom, { maxi: false, annie: false, christophe: false });
});

test('the $60 example: Maxi 10, Annie 35, Christophe the rest (15)', () => {
  const r = computeSplit({ ...custom60, customShares: { maxi: 1000, annie: 3500 } });
  assert.deepEqual(r.shares, { maxi: 1000, annie: 3500, christophe: 1500 });
  assert.deepEqual(r.custom, { maxi: true, annie: true, christophe: false });
  assert.equal(r.remainderCents, 1500);
});

test('custom amounts are read from a party document members map', () => {
  const r = computeSplit({
    ...custom60,
    members: { maxi: { customShareCents: 1000 }, annie: { customShareCents: 3500 }, christophe: { customShareCents: null } },
  });
  assert.deepEqual(r.shares, { maxi: 1000, annie: 3500, christophe: 1500 });
  assert.deepEqual(customSharesOf({ maxi: { customShareCents: 1000 }, annie: {}, christophe: { customShareCents: null } }), { maxi: 1000 });
});

test('clearing Annie: Maxi 10, the remaining 50 split between Annie and Christophe', () => {
  const r = computeSplit({ ...custom60, customShares: { maxi: 1000 } });
  assert.deepEqual(r.shares, { maxi: 1000, annie: 2500, christophe: 2500 });
});

test('leftover cents go to the host on the equal split, else to the first joinee on it', () => {
  // $100.00 + 18% = 11800; Annie 1000 -> 10800 over maxi and christophe: 5400 each, no leftover.
  const hostEqual = computeSplit({ billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: [...trio, 'dana'], customShares: { annie: 1001 } });
  // 10799 over maxi, christophe, dana: base 3599, leftover 2 -> host.
  assert.deepEqual(hostEqual.shares, { maxi: 3601, annie: 1001, christophe: 3599, dana: 3599 });
  const hostCustom = computeSplit({ billCents: 10000, tip: { type: 'percent', percent: 18 }, memberIds: [...trio, 'dana'], customShares: { maxi: 1001 } });
  // 10799 over annie, christophe, dana: leftover 2 -> annie (first joinee on the equal split).
  assert.deepEqual(hostCustom.shares, { maxi: 1001, annie: 3601, christophe: 3599, dana: 3599 });
  assert.equal(sum(hostEqual.shares), 11800);
  assert.equal(sum(hostCustom.shares), 11800);
});

test('every member custom, summing exactly to the total', () => {
  const r = computeSplit({ ...custom60, customShares: { maxi: 1000, annie: 3500, christophe: 1500 } });
  assert.deepEqual(r.shares, { maxi: 1000, annie: 3500, christophe: 1500 });
  assert.equal(r.remainderCents, 0);
});

test('a $0.00 custom share is valid and leaves the rest to the others', () => {
  const r = computeSplit({ ...custom60, customShares: { christophe: 0 } });
  assert.deepEqual(r.shares, { maxi: 3000, annie: 3000, christophe: 0 });
});

test('over-total and all-custom-not-summing are rejected', () => {
  assert.equal(customSharesProblem({ totalCents: 6000, memberIds: trio, customShares: { annie: 3500, christophe: 3500 } }), 'custom amounts exceed the total');
  assert.throws(() => computeSplit({ ...custom60, customShares: { annie: 3500, christophe: 3500 } }), RangeError);
  assert.match(customSharesProblem({ totalCents: 6000, memberIds: trio, customShares: { maxi: 1000, annie: 1000, christophe: 1000 } }), /add up/);
  assert.match(customSharesProblem({ totalCents: 6000, memberIds: trio, customShares: { annie: 12.5 } }), /non-negative integer/);
  assert.match(customSharesProblem({ totalCents: 6000, memberIds: trio, customShares: { annie: -1 } }), /non-negative integer/);
  assert.equal(customSharesProblem({ totalCents: 6000, memberIds: trio, customShares: { annie: 6000 } }), null);
});

test('with custom amounts, shares always sum to the total', () => {
  for (let bill = 0; bill < 3000; bill += 13) {
    for (let n = 2; n <= 8; n++) {
      const ids = Array.from({ length: n }, (_, i) => `u${i}`);
      const customShares = { u1: Math.floor(bill / 3) };
      const r = computeSplit({ billCents: bill, tip: { type: 'percent', percent: 20 }, memberIds: ids, customShares });
      assert.equal(sum(r.shares), r.totalCents);
    }
  }
});
