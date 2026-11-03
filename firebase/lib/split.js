// Reference implementation of the equal-split rule (docs/firestore-schema.md,
// "Split rule"). Integer cents only; the iOS and Android apps port this as-is.

function assertCents(value, name) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new TypeError(`${name} must be a non-negative integer number of cents, got ${value}`);
  }
}

/** Tip in cents for a party's `tip` map. Percent tips round half up. */
export function tipCents(billCents, tip) {
  assertCents(billCents, 'billCents');
  if (tip.type === 'custom') {
    assertCents(tip.amountCents, 'tip.amountCents');
    return tip.amountCents;
  }
  if (tip.type === 'percent') {
    if (!Number.isSafeInteger(tip.percent) || tip.percent < 0) throw new TypeError('tip.percent must be an integer');
    return Math.floor((billCents * tip.percent + 50) / 100);
  }
  throw new TypeError(`unknown tip.type ${tip.type}`);
}

/**
 * Shares for every member, in memberIds order (host first).
 * Returns { tipCents, totalCents, base, leftover, shares: { [uid]: cents } }.
 */
export function computeSplit({ billCents, tip, memberIds }) {
  if (!Array.isArray(memberIds) || memberIds.length === 0) throw new TypeError('memberIds must be non-empty');
  const tipValue = tipCents(billCents, tip);
  const totalCents = billCents + tipValue;
  const n = memberIds.length;
  const base = Math.floor(totalCents / n);
  const leftover = totalCents - base * n;
  const shares = {};
  memberIds.forEach((uid, i) => {
    shares[uid] = i === 0 ? base + leftover : base;
  });
  return { tipCents: tipValue, totalCents, base, leftover, shares };
}
