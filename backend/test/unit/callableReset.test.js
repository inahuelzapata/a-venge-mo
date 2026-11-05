// The callable protocol of the free reset endpoint (lib/callableReset.js).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { adminOptionsFromEnv, EndpointConfigError, handleResetRequest } from '../../lib/callableReset.js';

const quiet = {};
const done = async () => ({ removed: { parties: 1 }, seeded: { users: 3 } });

test('a POST resets and answers { result: { ok: true, resetAt, removed, seeded } }', async () => {
  const { status, body } = await handleResetRequest({ method: 'POST' }, done, quiet);
  assert.equal(status, 200);
  assert.equal(body.result.ok, true);
  assert.ok(Number.isFinite(Date.parse(body.result.resetAt)));
  assert.deepEqual(body.result.removed, { parties: 1 });
  assert.deepEqual(body.result.seeded, { users: 3 });
});

test('anything but POST is rejected with 400 and never resets', async () => {
  let called = false;
  const { status, body } = await handleResetRequest({ method: 'GET' }, async () => { called = true; }, quiet);
  assert.equal(status, 400);
  assert.equal(body.error.status, 'INVALID_ARGUMENT');
  assert.equal(called, false);
});

test('a failed reset is INTERNAL with the partial-reset message', async () => {
  const { status, body } = await handleResetRequest({ method: 'POST' }, async () => { throw new Error('batch 2 failed'); }, quiet);
  assert.equal(status, 500);
  assert.deepEqual(body.error, {
    status: 'INTERNAL',
    message: 'Demo reset did not complete (batch 2 failed). The database may be partially reset; tap Reset again.',
  });
});

test('a missing endpoint setting is FAILED_PRECONDITION and says nothing was reset', async () => {
  const run = async () => adminOptionsFromEnv({});
  const { status, body } = await handleResetRequest({ method: 'POST' }, run, quiet);
  assert.equal(status, 500);
  assert.equal(body.error.status, 'FAILED_PRECONDITION');
  assert.match(body.error.message, /missing RESET_PROJECT_ID, RESET_CLIENT_EMAIL, RESET_PRIVATE_KEY\. Nothing was reset\./);
});

test('credentials come from the env, with escaped newlines in the key restored', () => {
  const options = adminOptionsFromEnv({
    RESET_PROJECT_ID: 'p', RESET_CLIENT_EMAIL: 'sa@p.iam.gserviceaccount.com', RESET_PRIVATE_KEY: 'line1\\nline2',
  });
  assert.equal(options.projectId, 'p');
  assert.equal(options.credential.privateKey, 'line1\nline2');
  assert.throws(() => adminOptionsFromEnv({ RESET_PROJECT_ID: 'p' }), EndpointConfigError);
});

test('on the emulator only a project id is needed', () => {
  assert.deepEqual(adminOptionsFromEnv({ FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080', GCLOUD_PROJECT: 'demo-x' }), { projectId: 'demo-x' });
});
