// Constants from docs/firestore-schema.md. Keep both in sync (and firestore.rules).

export const COLLECTIONS = Object.freeze({
  users: 'users',
  merchants: 'merchants',
  parties: 'parties',
  joinCodes: 'joinCodes',
  transactions: 'transactions',
});

export const PARTY_STATUSES = Object.freeze(['open', 'paid', 'expired']);

/** 12 single-code-point emojis (no variation selectors or skin tones). */
export const EMOJI_PALETTE = Object.freeze(['🍕', '🍔', '🌮', '🍣', '🍩', '🍺', '🎉', '🔥', '🚀', '🎸', '🐶', '🌵']);

/** Two emojis, repeats allowed (as iOS JoinCode.random): 12² = 144 codes per merchant. */
export const JOIN_CODE_LENGTH = 2;
export const TIP_PERCENT_PRESETS = Object.freeze([15, 18, 20, 25]);
export const PARTY_TTL_MS = 5 * 60 * 1000;
export const MAX_WRONG_CODE_ATTEMPTS = 3;
export const TRANSACTION_SOURCES = Object.freeze(['seed', 'party']);

/** joinCodes/{id} for a merchant + code, e.g. "tonys-greenwich_🍕🍺". */
export function joinCodeLockId(merchantId, joinCode) {
  return `${merchantId}_${joinCode.join('')}`;
}

/** transactions/{id} for the payment a joinee makes when the host pays. */
export function partyTransactionId(partyId, payerId) {
  return `${partyId}_${payerId}`;
}
