// Shape checks mirroring firebase/firestore.rules. Each returns a list of
// problems; an empty list means the document is valid.
import {
  EMOJI_PALETTE,
  JOIN_CODE_LENGTH,
  PARTY_STATUSES,
  PARTY_TTL_MS,
  TIP_PERCENT_PRESETS,
  TRANSACTION_SOURCES,
} from './schema.js';

const isInt = (v) => Number.isSafeInteger(v);
const isNonNegInt = (v) => isInt(v) && v >= 0;
const isStr = (v) => typeof v === 'string' && v.length > 0;
const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** Accepts a Firestore Timestamp, a Date, or anything with toMillis(). */
function millis(v) {
  if (v instanceof Date) return v.getTime();
  if (v && typeof v.toMillis === 'function') return v.toMillis();
  return null;
}

function checkKeys(doc, required, optional, problems) {
  const allowed = new Set([...required, ...optional]);
  for (const k of required) if (!(k in doc)) problems.push(`missing field ${k}`);
  for (const k of Object.keys(doc)) if (!allowed.has(k)) problems.push(`unknown field ${k}`);
}

const PARTY_FIELDS = [
  'hostId', 'merchantId', 'joinCode', 'status', 'createdAt', 'expiresAt', 'paidAt',
  'billCents', 'tip', 'memberIds', 'members', 'wrongCodeAttempts',
];

export function validateParty(p) {
  const problems = [];
  if (!isPlainObject(p)) return ['party must be an object'];
  checkKeys(p, PARTY_FIELDS, [], problems);

  if (!isStr(p.hostId)) problems.push('hostId must be a non-empty string');
  if (!isStr(p.merchantId)) problems.push('merchantId must be a non-empty string');

  if (!Array.isArray(p.joinCode) || p.joinCode.length !== JOIN_CODE_LENGTH) {
    problems.push(`joinCode must be an array of exactly ${JOIN_CODE_LENGTH} emojis`);
  } else if (!p.joinCode.every((e) => EMOJI_PALETTE.includes(e))) {
    problems.push('joinCode contains an emoji outside the palette');
  }

  if (!PARTY_STATUSES.includes(p.status)) problems.push(`status must be one of ${PARTY_STATUSES.join(', ')}`);

  const created = millis(p.createdAt);
  const expires = millis(p.expiresAt);
  if (created === null) problems.push('createdAt must be a timestamp');
  if (expires === null) problems.push('expiresAt must be a timestamp');
  if (created !== null && expires !== null && expires - created !== PARTY_TTL_MS) {
    problems.push('expiresAt must equal createdAt + 5 minutes');
  }
  if (p.paidAt !== null && millis(p.paidAt) === null) problems.push('paidAt must be a timestamp or null');
  if (p.status === 'paid' && p.paidAt === null) problems.push('paidAt is required when status is paid');

  if (p.billCents !== null && !isNonNegInt(p.billCents)) problems.push('billCents must be a non-negative integer (cents) or null');

  if (p.tip !== null) {
    const t = p.tip;
    if (!isPlainObject(t)) problems.push('tip must be a map or null');
    else if (t.type === 'percent') {
      if (Object.keys(t).sort().join() !== 'percent,type') problems.push('percent tip must have exactly {type, percent}');
      if (!TIP_PERCENT_PRESETS.includes(t.percent)) problems.push(`tip.percent must be one of ${TIP_PERCENT_PRESETS.join(', ')}`);
    } else if (t.type === 'custom') {
      if (Object.keys(t).sort().join() !== 'amountCents,type') problems.push('custom tip must have exactly {type, amountCents}');
      if (!isNonNegInt(t.amountCents)) problems.push('tip.amountCents must be a non-negative integer');
    } else problems.push('tip.type must be percent or custom');
  }
  if (p.status === 'paid' && (p.billCents === null || p.tip === null)) problems.push('a paid party needs billCents and tip');

  if (!Array.isArray(p.memberIds) || p.memberIds.length === 0 || !p.memberIds.every(isStr)) {
    problems.push('memberIds must be a non-empty array of user ids');
  } else {
    if (p.memberIds[0] !== p.hostId) problems.push('memberIds must start with hostId');
    if (new Set(p.memberIds).size !== p.memberIds.length) problems.push('memberIds must not repeat');
  }
  if (!isPlainObject(p.members)) problems.push('members must be a map');
  else {
    const keys = Object.keys(p.members).sort().join();
    if (Array.isArray(p.memberIds) && keys !== [...p.memberIds].sort().join()) {
      problems.push('members keys must equal memberIds');
    }
    for (const [uid, m] of Object.entries(p.members)) {
      if (!isPlainObject(m) || typeof m.paid !== 'boolean' || millis(m.joinedAt) === null ||
          Object.keys(m).sort().join() !== 'joinedAt,paid') {
        problems.push(`members.${uid} must be {joinedAt: timestamp, paid: bool}`);
      }
    }
  }

  if (!isPlainObject(p.wrongCodeAttempts)) problems.push('wrongCodeAttempts must be a map');
  else for (const [uid, n] of Object.entries(p.wrongCodeAttempts)) {
    if (!isNonNegInt(n)) problems.push(`wrongCodeAttempts.${uid} must be a non-negative integer`);
  }
  return problems;
}

export function validateUser(id, u) {
  const problems = [];
  if (!isPlainObject(u)) return ['user must be an object'];
  checkKeys(u, ['username', 'displayName', 'avatar', 'balanceCents', 'friendIds'], [], problems);
  if (!/^[a-z0-9_]{3,20}$/.test(u.username ?? '')) problems.push('username must match ^[a-z0-9_]{3,20}$');
  if (u.username !== id) problems.push('username must equal the document id');
  if (!isStr(u.displayName) || u.displayName.length > 40) problems.push('displayName must be 1-40 chars');
  if (!isStr(u.avatar)) problems.push('avatar must be a non-empty string');
  if (!isInt(u.balanceCents)) problems.push('balanceCents must be an integer');
  if (!Array.isArray(u.friendIds) || !u.friendIds.every(isStr)) problems.push('friendIds must be an array of user ids');
  else if (u.friendIds.includes(id)) problems.push('friendIds must not contain the user');
  return problems;
}

export function validateTransaction(t) {
  const problems = [];
  if (!isPlainObject(t)) return ['transaction must be an object'];
  checkKeys(t, ['payerId', 'payeeId', 'participantIds', 'amountCents', 'memo', 'merchantId', 'merchantName',
    'partyId', 'createdAt', 'likesCount', 'commentsCount', 'source'], [], problems);
  if (!isStr(t.payerId) || !isStr(t.payeeId) || t.payerId === t.payeeId) problems.push('payerId and payeeId must be different user ids');
  if (!Array.isArray(t.participantIds) || t.participantIds.join() !== [t.payerId, t.payeeId].join()) {
    problems.push('participantIds must be [payerId, payeeId]');
  }
  if (!isInt(t.amountCents) || t.amountCents <= 0) problems.push('amountCents must be a positive integer');
  if (!isStr(t.memo) || t.memo.length > 80) problems.push('memo must be 1-80 chars');
  for (const k of ['merchantId', 'merchantName', 'partyId']) {
    if (t[k] !== null && !isStr(t[k])) problems.push(`${k} must be a string or null`);
  }
  if (millis(t.createdAt) === null) problems.push('createdAt must be a timestamp');
  if (!isNonNegInt(t.likesCount)) problems.push('likesCount must be a non-negative integer');
  if (!isNonNegInt(t.commentsCount)) problems.push('commentsCount must be a non-negative integer');
  if (!TRANSACTION_SOURCES.includes(t.source)) problems.push(`source must be one of ${TRANSACTION_SOURCES.join(', ')}`);
  return problems;
}
