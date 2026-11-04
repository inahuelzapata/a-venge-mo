// Deploys firestore.rules and firestore.indexes.json to the hosted project named
// in backend/.env. Used by `npm run deploy:firestore` and at the start of every
// hosted test run. The project always comes from FIREBASE_PROJECT_ID (there is
// no .firebaserc), so a deploy can never land on a project picked elsewhere.
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { BACKEND_DIR, childEnv, CliError, ConfigError } from './env.js';

const FIREBASE_BIN = join(BACKEND_DIR, 'node_modules', '.bin', process.platform === 'win32' ? 'firebase.cmd' : 'firebase');

export function deployRulesAndIndexes(config) {
  if (config.target !== 'hosted') {
    throw new ConfigError(
      `Deploy targets the hosted project only, but FIREBASE_TARGET=${config.target}. ` +
        'The emulator loads firestore.rules and firestore.indexes.json itself. Nothing was written.',
    );
  }
  console.log(`Deploying firestore.rules and firestore.indexes.json to HOSTED project ${config.projectId} ...`);
  const r = spawnSync(
    FIREBASE_BIN,
    ['deploy', '--only', 'firestore:rules,firestore:indexes', '--project', config.projectId, '--non-interactive'],
    { cwd: BACKEND_DIR, env: childEnv(config), stdio: 'inherit' },
  );
  if (r.error) throw new CliError(`Could not start the Firebase CLI (${r.error.message}). Run npm install in backend/.`);
  if (r.status !== 0) {
    throw new CliError(
      `firebase deploy failed (exit ${r.status}); see the output above.\n` +
        '  A 403 / "Permission denied" means the credential lacks deploy roles: grant its service account\n' +
        '  "Firebase Admin" and "Service Usage Consumer" in Google Cloud console > IAM (docs/integration-setup-guide.md, step 1).',
    );
  }
}
