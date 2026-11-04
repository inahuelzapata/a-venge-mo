// Target/credential checks (lib/env.js) and the scripts' fail-fast exit code.
// No database is touched: every case must be refused before any SDK call.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, chmodSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { ConfigError, readConfig, BACKEND_DIR } from '../../lib/env.js';

const dir = mkdtempSync(join(tmpdir(), 'split-party-env-'));
const emptyHome = join(dir, 'home-without-adc');
mkdirSync(emptyHome);
const key = (over = {}) => JSON.stringify({
  type: 'service_account', project_id: 'my-project', client_email: 'x@my-project.iam.gserviceaccount.com',
  private_key: 'dummy-not-a-real-key', ...over,
});
const goodKey = join(dir, 'service-account.json');
writeFileSync(goodKey, key());

const hosted = (over = {}) => ({ FIREBASE_TARGET: 'hosted', FIREBASE_PROJECT_ID: 'my-project', GOOGLE_APPLICATION_CREDENTIALS: goodKey, ...over });
const opts = { home: emptyHome, baseDir: dir };

function rejects(env, pattern) {
  assert.throws(() => readConfig(env, opts), (err) => err instanceof ConfigError && pattern.test(err.message));
}

test('hosted with a matching key file is accepted', () => {
  const c = readConfig(hosted(), opts);
  assert.deepEqual(c.credentials, { kind: 'key', path: goodKey });
  assert.equal(c.emulatorHost, null);
});

test('a relative key path resolves from backend/, not the cwd', () => {
  const c = readConfig(hosted({ GOOGLE_APPLICATION_CREDENTIALS: 'service-account.json' }), opts);
  assert.equal(c.credentials.path, goodKey);
});

test('hosted with no key and no application-default credentials is refused', () =>
  rejects(hosted({ GOOGLE_APPLICATION_CREDENTIALS: '' }), /No credentials/));

test('hosted with no key falls back to application-default credentials', () => {
  const home = join(dir, 'home-with-adc');
  mkdirSync(join(home, '.config', 'gcloud'), { recursive: true });
  writeFileSync(join(home, '.config', 'gcloud', 'application_default_credentials.json'), '{}');
  const c = readConfig(hosted({ GOOGLE_APPLICATION_CREDENTIALS: undefined }), { ...opts, home });
  assert.equal(c.credentials.kind, 'adc');
});

test('a key path that does not exist is refused', () =>
  rejects(hosted({ GOOGLE_APPLICATION_CREDENTIALS: join(dir, 'nope.json') }), /does not exist/));

test('a key file that is not JSON is refused', () => {
  const p = join(dir, 'garbage.json');
  writeFileSync(p, 'not json');
  rejects(hosted({ GOOGLE_APPLICATION_CREDENTIALS: p }), /not valid JSON/);
});

test('an unreadable key file is refused', { skip: process.getuid?.() === 0 }, () => {
  const p = join(dir, 'locked.json');
  writeFileSync(p, key());
  chmodSync(p, 0o000);
  rejects(hosted({ GOOGLE_APPLICATION_CREDENTIALS: p }), /cannot be read/);
});

test('a JSON file that is not a service-account key is refused', () => {
  const p = join(dir, 'google-services.json');
  writeFileSync(p, JSON.stringify({ project_info: { project_id: 'my-project' } }));
  rejects(hosted({ GOOGLE_APPLICATION_CREDENTIALS: p }), /not a service-account key/);
});

test('a key for another project is refused', () => {
  const p = join(dir, 'other.json');
  writeFileSync(p, key({ project_id: 'someone-else' }));
  rejects(hosted({ GOOGLE_APPLICATION_CREDENTIALS: p }), /key for project "someone-else"/);
});

test('hosted with the emulator host still set is refused', () =>
  rejects(hosted({ FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' }), /FIRESTORE_EMULATOR_HOST is set but FIREBASE_TARGET=hosted/));

test('hosted with the emulator demo project id is refused', () =>
  rejects(hosted({ FIREBASE_PROJECT_ID: 'demo-split-party' }), /emulator-only demo id/));

test('emulator needs its host, and no credentials', () => {
  rejects({ FIREBASE_TARGET: 'emulator', FIREBASE_PROJECT_ID: 'demo-split-party' }, /FIRESTORE_EMULATOR_HOST is not set/);
  const c = readConfig({ FIREBASE_TARGET: 'emulator', FIREBASE_PROJECT_ID: 'demo-split-party', FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' }, opts);
  assert.equal(c.credentials, null);
});

// The scripts themselves: exit code 2 and a message, before firebase-admin is even loaded.
const base = { PATH: process.env.PATH, HOME: emptyHome };
for (const script of ['seed', 'reset', 'deploy-firestore', 'test-hosted']) {
  for (const [name, env, pattern] of [
    ['no target', {}, /FIREBASE_TARGET is not set/],
    ['missing credential', hosted({ GOOGLE_APPLICATION_CREDENTIALS: join(dir, 'missing.json') }), /does not exist/],
    ['emulator host still set', hosted({ FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' }), /FIRESTORE_EMULATOR_HOST is set/],
  ]) {
    test(`${script} with ${name} exits 2 before any database call`, () => {
      const args = script === 'test-hosted' ? ['--wipe-demo-db'] : script === 'reset' ? ['--yes'] : [];
      const r = spawnSync(process.execPath, [`scripts/${script}.js`, ...args], {
        cwd: BACKEND_DIR, env: { ...base, ...env }, encoding: 'utf8',
      });
      assert.equal(r.status, 2, r.stderr);
      assert.match(r.stderr, pattern);
      assert.match(r.stderr, /Nothing was written/);
    });
  }
}

// Destructive commands need their confirmation flag even with a complete hosted config.
for (const [script, pattern] of [
  ['reset', /Refusing to reset HOSTED project my-project without confirmation/],
  ['test-hosted', /Refusing to start: this test run DELETES every live party on HOSTED project my-project[\s\S]*--wipe-demo-db/],
]) {
  test(`${script} against hosted without its confirmation flag refuses and exits 2`, () => {
    const r = spawnSync(process.execPath, [`scripts/${script}.js`], {
      cwd: BACKEND_DIR, env: { ...base, ...hosted() }, encoding: 'utf8',
    });
    assert.equal(r.status, 2, r.stderr);
    assert.match(r.stderr, pattern);
  });
}

test('test-hosted against the emulator is refused (rules suite has its own command)', () => {
  const r = spawnSync(process.execPath, ['scripts/test-hosted.js', '--wipe-demo-db'], {
    cwd: BACKEND_DIR,
    env: { ...base, FIREBASE_TARGET: 'emulator', FIREBASE_PROJECT_ID: 'demo-split-party', FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' },
    encoding: 'utf8',
  });
  assert.equal(r.status, 2, r.stderr);
  assert.match(r.stderr, /npm run test:rules/);
});
