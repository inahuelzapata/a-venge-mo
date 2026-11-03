// Resolves which Firestore to talk to from the environment, and refuses to
// continue (before any SDK call) when the configuration is incomplete.
import { existsSync } from 'node:fs';

export class ConfigError extends Error {}

const TARGETS = ['emulator', 'hosted'];

/**
 * Reads and checks the environment. Returns a plain config object, or throws
 * ConfigError naming every missing/invalid variable.
 */
export function readConfig(env = process.env) {
  const problems = [];
  const target = env.FIREBASE_TARGET;
  const projectId = env.FIREBASE_PROJECT_ID;

  if (!target) {
    problems.push(`FIREBASE_TARGET is not set (use one of: ${TARGETS.join(', ')}).`);
  } else if (!TARGETS.includes(target)) {
    problems.push(`FIREBASE_TARGET="${target}" is invalid (use one of: ${TARGETS.join(', ')}).`);
  }
  if (!projectId) problems.push('FIREBASE_PROJECT_ID is not set (the Firebase project to target).');

  if (target === 'emulator') {
    if (!env.FIRESTORE_EMULATOR_HOST) {
      problems.push('FIRESTORE_EMULATOR_HOST is not set (e.g. 127.0.0.1:8080) but FIREBASE_TARGET=emulator.');
    }
  }
  if (target === 'hosted') {
    if (env.FIRESTORE_EMULATOR_HOST) {
      problems.push(
        'FIRESTORE_EMULATOR_HOST is set but FIREBASE_TARGET=hosted; unset it, otherwise the SDK would silently write to the emulator.',
      );
    }
    const creds = env.GOOGLE_APPLICATION_CREDENTIALS;
    if (!creds) {
      problems.push('GOOGLE_APPLICATION_CREDENTIALS is not set (path to a service-account key kept outside the repo).');
    } else if (!existsSync(creds)) {
      problems.push(`GOOGLE_APPLICATION_CREDENTIALS points to "${creds}", which does not exist.`);
    }
  }

  if (problems.length) {
    throw new ConfigError(
      'Firestore target is not configured. Nothing was written.\n' +
        problems.map((p) => `  - ${p}`).join('\n') +
        '\nSee backend/.env.example.',
    );
  }
  return { target, projectId, emulatorHost: env.FIRESTORE_EMULATOR_HOST ?? null };
}

/** Opens Firestore for a checked config. firebase-admin is imported lazily so a bad config never loads it. */
export async function openFirestore(config) {
  const { initializeApp, getApps } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');
  const app = getApps()[0] ?? initializeApp({ projectId: config.projectId });
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
    console.error(err);
    process.exitCode = 1;
  }
}
