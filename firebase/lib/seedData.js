// The demo's canonical starting state. Every document has a FIXED id and FIXED
// values (including timestamps), which is what makes seed idempotent and lets
// reset restore exactly this state.

export const MERCHANT_ID = 'tonys-greenwich';

export const SEED_USERS = Object.freeze({
  maxi: { displayName: 'Maxi', avatar: 'avatar_maxi', balanceCents: 25000 },
  annie: { displayName: 'Annie', avatar: 'avatar_annie', balanceCents: 18050 },
  christophe: { displayName: 'Christophe', avatar: 'avatar_christophe', balanceCents: 32075 },
});

// [id, payer, payee, amountCents, memo, fromTonys, ISO time, likes, comments]
const SEED_FEED = [
  ['seed_tx_01', 'annie', 'maxi', 1850, '🌮 Taco Tuesday', false, '2026-10-27T23:30:00Z', 2, 0],
  ['seed_tx_02', 'christophe', 'annie', 4200, 'Concert tickets 🎸', false, '2026-10-28T18:05:00Z', 3, 1],
  ['seed_tx_03', 'maxi', 'christophe', 1200, 'Coffee ☕', false, '2026-10-29T13:15:00Z', 1, 0],
  ['seed_tx_04', 'christophe', 'maxi', 2575, "Tony's Split Party", true, '2026-10-30T00:40:00Z', 2, 2],
  ['seed_tx_05', 'annie', 'christophe', 3000, 'Uber home 🚕', false, '2026-10-31T03:20:00Z', 0, 0],
  ['seed_tx_06', 'maxi', 'annie', 900, '🍦 ice cream run', false, '2026-11-01T20:10:00Z', 1, 1],
];

/**
 * Builds the seed documents. `Timestamp` and `GeoPoint` come from the Firestore
 * SDK in use, so this module stays free of SDK imports.
 * Returns { users, merchants, transactions } as { [id]: data } maps.
 */
export function buildSeed({ Timestamp, GeoPoint }) {
  const ids = Object.keys(SEED_USERS);
  const users = {};
  for (const id of ids) {
    users[id] = { username: id, ...SEED_USERS[id], friendIds: ids.filter((other) => other !== id) };
  }

  const merchants = {
    [MERCHANT_ID]: {
      name: "Tony's",
      city: 'Greenwich, CT',
      logoRef: 'merchant_tonys',
      offer: 'Start a Split Party at Tony\'s and everyone settles up in one tap.',
      geofence: { center: new GeoPoint(41.0262, -73.6282), radiusMeters: 150 },
    },
  };

  const transactions = {};
  for (const [id, payerId, payeeId, amountCents, memo, fromTonys, iso, likesCount, commentsCount] of SEED_FEED) {
    transactions[id] = {
      payerId,
      payeeId,
      participantIds: [payerId, payeeId],
      amountCents,
      memo,
      merchantId: fromTonys ? MERCHANT_ID : null,
      merchantName: fromTonys ? "Tony's" : null,
      partyId: null,
      createdAt: Timestamp.fromDate(new Date(iso)),
      likesCount,
      commentsCount,
      source: 'seed',
    };
  }
  return { users, merchants, transactions };
}
