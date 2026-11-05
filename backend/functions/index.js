// Cloud Functions for the Split Party PoC.
//
// resetDemo — the in-app "Demo reset" button. Runs the SAME reset as
// `npm run reset` (backend/lib/ops.js, copied into ./shared at deploy time by
// scripts/build-functions.js) with admin rights, so the client security rules
// do not apply. Open callable on purpose: no auth or key check (PoC demo
// database only; security hardening is out of scope).
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions';
import { reset } from './shared/ops.js';
import { partialResetMessage } from './shared/callableReset.js';

/** The apps call this name in this region (the Firebase SDKs' default region). */
const RESET_DEMO_REGION = 'us-central1';

initializeApp();

export const resetDemo = onCall({ region: RESET_DEMO_REGION, timeoutSeconds: 60 }, async () => {
  try {
    const { removed, seeded } = await reset(getFirestore());
    const resetAt = new Date().toISOString();
    logger.info('Demo reset done', { resetAt, removed, seeded });
    return { ok: true, resetAt, removed, seeded };
  } catch (err) {
    // reset commits in several batches, so a failure can leave it half done.
    // Say so: calling again is safe (reset is idempotent) and finishes the job.
    logger.error('Demo reset failed', err);
    throw new HttpsError('internal', partialResetMessage(err));
  }
});
