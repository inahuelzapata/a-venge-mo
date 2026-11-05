// The Demo reset over the Firebase *callable* wire protocol, for hosting it
// anywhere Node runs (the free reset endpoint in reset-endpoint/, used when the
// project is not on Blaze and Cloud Functions can't be deployed).
//
// Protocol (what the Firebase SDKs' callable clients send and expect):
//   request   POST, JSON body { "data": null }  (no arguments, no auth)
//   success   200 { "result": { ok: true, resetAt, removed, seeded } }
//   failure   non-2xx { "error": { "status": "<CODE>", "message": "<text>" } }
// So the apps call it with the same SDK and error handling as the
// resetDemo Cloud Function, only by URL instead of by name.
//
// Copied into functions/shared/ and reset-endpoint/shared/ by
// scripts/build-functions.js: edit this file, never the copies.

/** The text a failed reset shows on the phone; resetting again is safe (reset is idempotent). */
export function partialResetMessage(err) {
  return `Demo reset did not complete (${err?.message ?? err}). The database may be partially reset; tap Reset again.`;
}

/** The endpoint's own setup is incomplete: nothing was touched. */
export class EndpointConfigError extends Error {}

/**
 * firebase-admin options from the endpoint's environment. On the Firestore
 * emulator (FIRESTORE_EMULATOR_HOST set) only a project id is needed.
 */
export function adminOptionsFromEnv(env) {
  if (env.FIRESTORE_EMULATOR_HOST) {
    const projectId = env.RESET_PROJECT_ID || env.GCLOUD_PROJECT;
    if (!projectId) throw new EndpointConfigError('Reset endpoint is not configured: missing RESET_PROJECT_ID.');
    return { projectId };
  }
  const missing = ['RESET_PROJECT_ID', 'RESET_CLIENT_EMAIL', 'RESET_PRIVATE_KEY'].filter((k) => !env[k]);
  if (missing.length) {
    throw new EndpointConfigError(`Reset endpoint is not configured: missing ${missing.join(', ')}. Nothing was reset.`);
  }
  return {
    projectId: env.RESET_PROJECT_ID,
    credential: {
      projectId: env.RESET_PROJECT_ID,
      clientEmail: env.RESET_CLIENT_EMAIL,
      // Host env vars often keep the key's newlines as the two characters "\n".
      privateKey: env.RESET_PRIVATE_KEY.replace(/\\n/g, '\n'),
    },
  };
}

function callableError(status, code, message) {
  return { status, body: { error: { status: code, message } } };
}

/**
 * Handles one request. [runReset] performs the reset and returns
 * { removed, seeded } (ops.js reset); it is only called for a POST.
 * Returns { status, body } for the host adapter to send as JSON.
 */
export async function handleResetRequest({ method }, runReset, log = console) {
  if (method !== 'POST') {
    return callableError(400, 'INVALID_ARGUMENT', 'resetDemo is a callable function: send POST {"data":null}.');
  }
  try {
    const { removed, seeded } = await runReset();
    const resetAt = new Date().toISOString();
    log.info?.('Demo reset done', JSON.stringify({ resetAt, removed, seeded }));
    return { status: 200, body: { result: { ok: true, resetAt, removed, seeded } } };
  } catch (err) {
    log.error?.('Demo reset failed', err);
    if (err instanceof EndpointConfigError) return callableError(500, 'FAILED_PRECONDITION', err.message);
    return callableError(500, 'INTERNAL', partialResetMessage(err));
  }
}
