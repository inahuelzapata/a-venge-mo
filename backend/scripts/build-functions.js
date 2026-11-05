// Copies the shared reset implementation (lib/ops.js and the modules it imports,
// plus the callable protocol in lib/callableReset.js) into functions/shared/ and
// reset-endpoint/shared/, because Cloud Functions only uploads functions/ and the
// free reset endpoint only uploads reset-endpoint/.
// Runs as the firebase.json functions predeploy, before deploy:reset-endpoint and
// before the tests, so both always run the same code as `npm run reset`.
// Both shared/ folders are generated and git-ignored: edit backend/lib, never a copy.
import { copyFileSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { BACKEND_DIR } from '../lib/env.js';

export const SHARED_MODULES = ['ops.js', 'schema.js', 'seedData.js', 'callableReset.js'];

for (const target of ['functions', 'reset-endpoint']) {
  const out = join(BACKEND_DIR, target, 'shared');
  rmSync(out, { recursive: true, force: true });
  mkdirSync(out, { recursive: true });
  for (const f of SHARED_MODULES) copyFileSync(join(BACKEND_DIR, 'lib', f), join(out, f));
}
console.log(`Copied lib/{${SHARED_MODULES.join(',')}} into functions/shared/ and reset-endpoint/shared/.`);
