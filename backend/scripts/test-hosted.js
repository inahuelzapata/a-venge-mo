// npm run test:hosted -- --wipe-demo-db
//
// Runs the acceptance suite against the HOSTED demo database in backend/.env:
//   1. deploy the repo's firestore.rules + firestore.indexes.json
//   2. reset to the seeded state, and wait until the composite indexes serve queries
//   3. node --test test/acceptance/  (wipes and rewrites the database)
//   4. reset to the seeded state again: always, whether the tests passed, failed or were interrupted
// Because it deletes every live party, it refuses to start without --wipe-demo-db.
import { spawn } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ConfigError, BACKEND_DIR, childEnv, describeTarget, openFirestore, readConfig, runCli } from '../lib/env.js';
import { deployRulesAndIndexes } from '../lib/deploy.js';

const CONFIRM_FLAG = '--wipe-demo-db';
const INDEX_WAIT_MS = 15 * 60 * 1000;
const INDEX_POLL_MS = 15 * 1000;

/** "transactions:participantIds:CONTAINS,createdAt:DESCENDING" — comparable key for one composite index. */
function indexKey(collectionGroup, fields) {
  const parts = fields
    .filter((f) => f.fieldPath !== '__name__')
    .map((f) => `${f.fieldPath}:${f.order ?? f.arrayConfig}`);
  return `${collectionGroup}:${parts.join(',')}`;
}

/**
 * Waits until every composite index in firestore.indexes.json is READY on the
 * hosted project, asking the Firestore admin API directly. Probing with
 * queries is not reliable: Firestore can answer a small or empty query
 * without the index while the real query still fails with "index is building".
 */
async function waitForIndexes(config) {
  const declared = JSON.parse(readFileSync(join(BACKEND_DIR, 'firestore.indexes.json'), 'utf8')).indexes
    .map((i) => indexKey(i.collectionGroup, i.fields));
  const { getApp } = await import('firebase-admin/app');
  const credential = getApp().options.credential;
  const url = `https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/collectionGroups/-/indexes`;
  const deadline = Date.now() + INDEX_WAIT_MS;
  for (;;) {
    const { access_token: token } = await credential.getAccessToken();
    const res = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
    if (!res.ok) throw new Error(`Could not list Firestore indexes (HTTP ${res.status}): ${await res.text()}`);
    const states = new Map(((await res.json()).indexes ?? []).map((i) => [
      indexKey(i.name.split('/collectionGroups/')[1].split('/')[0], i.fields), i.state,
    ]));
    const pending = declared.filter((k) => states.get(k) !== 'READY');
    if (!pending.length) {
      console.log(`All ${declared.length} composite indexes are READY.`);
      return;
    }
    if (Date.now() > deadline) throw new Error(`Indexes still not READY after ${INDEX_WAIT_MS / 60000} min: ${pending.join('; ')}`);
    for (const k of pending) console.log(`Index ${states.get(k) ?? 'MISSING'}: ${k} (checking again in ${INDEX_POLL_MS / 1000}s)`);
    await new Promise((r) => setTimeout(r, INDEX_POLL_MS));
  }
}

function runAcceptance(config, extraArgs) {
  return new Promise((resolve) => {
    const child = spawn(
      process.execPath,
      ['--test', '--test-concurrency=1', ...extraArgs, 'test/acceptance/'],
      { cwd: BACKEND_DIR, env: { ...childEnv(config), SPLIT_PARTY_ACCEPTANCE_RUNNER: '1' }, stdio: 'inherit' },
    );
    child.on('exit', (code, signal) => resolve(code ?? (signal ? 1 : 0)));
  });
}

await runCli(async () => {
  const config = readConfig(); // throws before anything connects if env is incomplete
  if (config.target !== 'hosted') {
    throw new ConfigError(
      `test:hosted runs against the hosted project, but FIREBASE_TARGET=${config.target}. Nothing was written.\n` +
        '  The security-rules suite runs under the emulator with: npm run test:rules',
    );
  }
  const args = process.argv.slice(2);
  if (!args.includes(CONFIRM_FLAG)) {
    console.error(
      `Refusing to start: this test run DELETES every live party on ${describeTarget(config)} ` +
        '(the demo database) and rewrites the seeded data. Do not run it during a demo.\n' +
        `Re-run with the confirmation flag:  npm run test:hosted -- ${CONFIRM_FLAG}`,
    );
    process.exitCode = 2;
    return;
  }

  // Ctrl-C reaches the test process too; we stay alive so the final reset still runs.
  let interrupted = false;
  process.on('SIGINT', () => { interrupted = true; });

  deployRulesAndIndexes(config);
  const db = await openFirestore(config);
  const { reset } = await import('../lib/ops.js');

  let status = 1;
  try {
    console.log(`Resetting ${describeTarget(config)} to the seeded state before the tests ...`);
    await reset(db);
    console.log('Waiting for the composite indexes to be READY ...');
    await waitForIndexes(config);
    if (!interrupted) status = await runAcceptance(config, args.filter((a) => a !== CONFIRM_FLAG));
  } finally {
    console.log(`Resetting ${describeTarget(config)} to the seeded state after the tests ...`);
    try {
      const { seeded } = await reset(db);
      console.log(`Database is back in the seeded state: ${seeded.users} users, ${seeded.merchants} merchant, ` +
        `${seeded.transactions} feed transactions, no parties.`);
    } catch (err) {
      console.error('ERROR: the final reset FAILED; the demo database may hold test data. Run: npm run reset -- --yes');
      throw err;
    }
  }
  process.exitCode = interrupted ? 130 : status;
});
