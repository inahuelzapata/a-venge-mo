// POST https://<deployment>/api/resetDemo — the in-app Demo reset for projects
// without Blaze (no Cloud Functions). Same reset as `npm run reset`
// (shared/ops.js, copied from backend/lib) and the same callable protocol as
// the resetDemo Cloud Function (shared/callableReset.js), so the apps call it
// with the Firebase Functions SDK by URL. Open on purpose, like the callable:
// no auth (PoC demo database only).
//
// Credentials come from the Vercel project's environment variables
// RESET_PROJECT_ID, RESET_CLIENT_EMAIL and RESET_PRIVATE_KEY, which
// `npm run deploy:reset-endpoint` sets from backend/service-account.json.
import { cert, getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { adminOptionsFromEnv, handleResetRequest } from '../shared/callableReset.js';
import { reset } from '../shared/ops.js';

function database() {
  if (!getApps().length) {
    const { projectId, credential } = adminOptionsFromEnv(process.env);
    initializeApp(credential ? { projectId, credential: cert(credential) } : { projectId });
  }
  return getFirestore();
}

export default async function handler(req, res) {
  const { status, body } = await handleResetRequest({ method: req.method }, () => reset(database()));
  res.statusCode = status;
  res.setHeader('content-type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(body));
}
