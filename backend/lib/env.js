// Resolves which Firestore to talk to from the environment, and refuses to
// continue (before any SDK call) when the configuration is incomplete or
// contradictory. Convention: docs/firestore-target.md
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { isAbsolute, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export class ConfigError extends Error {}

/** An expected failure with its own explanation: printed without a stack trace, exit code 1. */
export class CliError extends Error {}

const TARGETS = ['emulator', 'hosted'];

/** backend/ — relative key paths in .env are resolved from here, not from the shell's cwd. */
export const BACKEND_DIR = fileURLToPath(new URL('..', import.meta.url));

/** The documented default key location (git-ignored by service-account*.json). */
export const DEFAULT_KEY_FILE = 'service-account.json';

/** Where `gcloud auth application-default login` writes the developer's credentials. */
export function adcWellKnownFile(env = process.env, home = homedir()) {
  if (env.CLOUDSDK_CONFIG) return join(env.CLOUDSDK_CONFIG, 'application_default_credentials.json');
  if (process.platform === 'win32' && env.APPDATA) return join(env.APPDATA, 'gcloud', 'application_default_credentials.json');
  return join(home, '.config', 'gcloud', 'application_default_credentials.json');
}

/** Checks a service-account key file; returns a list of problems (empty when usable). */
function checkKeyFile(path, projectId) {
  let key;
  try {
    key = JSON.parse(readFileSync(path, 'utf8'));
  } catch (err) {
    if (err.code === 'ENOENT') return [`GOOGLE_APPLICATION_CREDENTIALS points to "${path}", which does not exist.`];
    if (err instanceof SyntaxError) return [`GOOGLE_APPLICATION_CREDENTIALS points to "${path}", which is not valid JSON.`];
    return [`GOOGLE_APPLICATION_CREDENTIALS points to "${path}", which cannot be read (${err.code ?? err.message}).`];
  }
  if (key?.type !== 'service_account' || !key.client_email || !key.private_key) {
    return [`"${path}" is not a service-account key (download one from Firebase console > Project settings > Service accounts).`];
  }
  if (projectId && key.project_id !== projectId) {
    return [`"${path}" is a key for project "${key.project_id}" but FIREBASE_PROJECT_ID is "${projectId}".`];
  }
  return [];
}

/**
 * Reads and checks the environment. Returns a plain config object, or throws
 * ConfigError naming every missing/invalid variable.
 *
 * Hosted credentials, in order: GOOGLE_APPLICATION_CREDENTIALS (a key file, the
 * documented default), else the developer's application-default credentials.
 * A key path that is set but unusable is always an error, never silently skipped.
 */
export function readConfig(env = process.env, { home = homedir(), baseDir = BACKEND_DIR } = {}) {
  const problems = [];
  const target = env.FIREBASE_TARGET?.trim();
  const projectId = env.FIREBASE_PROJECT_ID?.trim();
  const emulatorHost = env.FIRESTORE_EMULATOR_HOST?.trim() || null;
  let credentials = null;

  if (!target) {
    problems.push(`FIREBASE_TARGET is not set (use one of: ${TARGETS.join(', ')}).`);
  } else if (!TARGETS.includes(target)) {
    problems.push(`FIREBASE_TARGET="${target}" is invalid (use one of: ${TARGETS.join(', ')}).`);
  }
  if (!projectId) problems.push('FIREBASE_PROJECT_ID is not set (the Firebase project to target).');
  if (projectId === 'your-firebase-project-id') {
    problems.push('FIREBASE_PROJECT_ID is still the template placeholder; put your Firebase project id in backend/.env.');
  }

  if (target === 'emulator' && !emulatorHost) {
    problems.push('FIRESTORE_EMULATOR_HOST is not set (e.g. 127.0.0.1:8080) but FIREBASE_TARGET=emulator.');
  }
  if (target === 'hosted') {
    if (env.FIRESTORE_EMULATOR_HOST !== undefined && env.FIRESTORE_EMULATOR_HOST !== '') {
      problems.push(
        'FIRESTORE_EMULATOR_HOST is set but FIREBASE_TARGET=hosted; unset it, otherwise the SDK would silently write to the emulator.',
      );
    }
    if (projectId?.startsWith('demo-')) {
      problems.push(`FIREBASE_PROJECT_ID="${projectId}" is an emulator-only demo id; use your Firebase project id for hosted.`);
    }
    const keyPath = env.GOOGLE_APPLICATION_CREDENTIALS?.trim();
    if (keyPath) {
      const path = isAbsolute(keyPath) ? keyPath : resolve(baseDir, keyPath);
      const keyProblems = checkKeyFile(path, projectId);
      problems.push(...keyProblems);
      if (!keyProblems.length) credentials = { kind: 'key', path };
    } else {
      const adc = adcWellKnownFile(env, home);
      try {
        readFileSync(adc);
        credentials = { kind: 'adc', path: adc };
      } catch {
        problems.push(
          `No credentials: set GOOGLE_APPLICATION_CREDENTIALS=${DEFAULT_KEY_FILE} and put the service-account key at ` +
            `backend/${DEFAULT_KEY_FILE}, or run \`gcloud auth application-default login\`.`,
        );
      }
    }
  }

  if (problems.length) {
    throw new ConfigError(
      'Firestore target is not configured. Nothing was written.\n' +
        problems.map((p) => `  - ${p}`).join('\n') +
        '\nSee backend/.env.example and docs/integration-setup-guide.md.',
    );
  }
  return { target, projectId, emulatorHost, credentials };
}

/** Environment for child tools (firebase CLI, test runs) with the key path made absolute. */
export function childEnv(config, env = process.env) {
  const out = { ...env };
  if (config.credentials?.kind === 'key') out.GOOGLE_APPLICATION_CREDENTIALS = config.credentials.path;
  else delete out.GOOGLE_APPLICATION_CREDENTIALS;
  return out;
}

/** Opens Firestore for a checked config. firebase-admin is imported lazily so a bad config never loads it. */
export async function openFirestore(config) {
  const { initializeApp, getApps, cert, applicationDefault } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');
  const options = { projectId: config.projectId };
  if (config.credentials?.kind === 'key') options.credential = cert(config.credentials.path);
  else if (config.credentials?.kind === 'adc') options.credential = applicationDefault();
  const app = getApps()[0] ?? initializeApp(options);
  return getFirestore(app);
}

export function describeTarget(config) {
  return config.target === 'emulator'
    ? `emulator ${config.emulatorHost} (project ${config.projectId})`
    : `HOSTED project ${config.projectId}`;
}

/** Shared CLI wrapper: prints a clean message for config errors and sets the exit code. */
export async function runCli(main) {
  try {
    await main();
  } catch (err) {
    if (err instanceof ConfigError) {
      console.error(`ERROR: ${err.message}`);
      process.exitCode = 2;
      return;
    }
    console.error(err instanceof CliError ? `ERROR: ${err.message}` : err);
    process.exitCode = 1;
  }
}
