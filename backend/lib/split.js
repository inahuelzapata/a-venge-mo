// Reference implementation of the split rule (docs/firestore-schema.md,
// "Split rule", with custom shares). Integer cents only; the iOS and Android apps port this as-is.

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
 * Custom amounts by member from a party's `members` map: `{ [uid]: cents }` for
 * every member whose `customShareCents` is set (absent or null = equal split).
 */
export function customSharesOf(members) {
  const custom = {};
  for (const [uid, m] of Object.entries(members ?? {})) {
    if (m && m.customShareCents !== undefined && m.customShareCents !== null) custom[uid] = m.customShareCents;
  }
  return custom;
}

/**
 * Why a set of custom amounts cannot be used for `totalCents`, or null when it
 * can (docs/firestore-schema.md, "Custom shares"): each amount is an integer
 * number of cents >= 0, they never sum past the total, and when every member
 * has one they sum to the total exactly. `customShares` is `{ [uid]: cents }`
 * (members not listed are on the equal split).
 */
export function customSharesProblem({ totalCents, memberIds, customShares }) {
  let sum = 0;
  let customCount = 0;
  for (const uid of memberIds) {
    const value = customShares?.[uid];
    if (value === undefined || value === null) continue;
    if (!Number.isSafeInteger(value) || value < 0) return `customShareCents of ${uid} must be a non-negative integer`;
    sum += value;
    customCount += 1;
  }
  if (sum > totalCents) return 'custom amounts exceed the total';
  if (customCount === memberIds.length && sum !== totalCents) return 'every member has a custom amount but they do not add up to the total';
  return null;
}

/**
 * Shares for every member, in memberIds order (host first). Custom amounts
 * come from `customShares` ({ [uid]: cents }) or, when it is not given, from
 * `members.{uid}.customShareCents` (so a party document can be passed as is).
 *
 * Extended split rule: a member with a custom amount pays exactly that; the
 * rest of the total is split equally among the others (`base`), and the
 * `leftover` cents go to the first of them in memberIds order (the host if the
 * host is on the equal split, else the first joinee on it). Shares always sum
 * to the total. Invalid custom amounts throw a RangeError.
 *
 * Returns { tipCents, totalCents, base, leftover, remainderCents, custom: { [uid]: bool }, shares: { [uid]: cents } }.
 */
export function computeSplit({ billCents, tip, memberIds, members, customShares }) {
  if (!Array.isArray(memberIds) || memberIds.length === 0) throw new TypeError('memberIds must be non-empty');
  const tipValue = tipCents(billCents, tip);
  const totalCents = billCents + tipValue;
  const custom = customShares ?? customSharesOf(members);
  const problem = customSharesProblem({ totalCents, memberIds, customShares: custom });
  if (problem) throw new RangeError(problem);
  const isCustom = (uid) => custom[uid] !== undefined && custom[uid] !== null;
  const equalIds = memberIds.filter((uid) => !isCustom(uid));
  const remainderCents = totalCents - memberIds.filter(isCustom).reduce((sum, uid) => sum + custom[uid], 0);
  const n = equalIds.length;
  const base = n === 0 ? 0 : Math.floor(remainderCents / n);
  const leftover = remainderCents - base * n;
  const shares = {};
  const customFlags = {};
  memberIds.forEach((uid) => {
    customFlags[uid] = isCustom(uid);
    if (isCustom(uid)) shares[uid] = custom[uid];
    else shares[uid] = uid === equalIds[0] ? base + leftover : base;
  });
  return { tipCents: tipValue, totalCents, base, leftover, remainderCents, custom: customFlags, shares };
}
