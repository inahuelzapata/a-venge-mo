// Copies the shared reset implementation (lib/ops.js and the modules it imports)
// into functions/shared/, because Cloud Functions only uploads functions/.
// Runs as the firebase.json functions predeploy and before test:functions, so
// the deployed resetDemo always runs the same code as `npm run reset`.
// functions/shared/ is generated and git-ignored: edit backend/lib, never the copy.
import { copyFileSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { BACKEND_DIR } from '../lib/env.js';

export const SHARED_MODULES = ['ops.js', 'schema.js', 'seedData.js'];

const out = join(BACKEND_DIR, 'functions', 'shared');
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
for (const f of SHARED_MODULES) copyFileSync(join(BACKEND_DIR, 'lib', f), join(out, f));
console.log(`Copied lib/{${SHARED_MODULES.join(',')}} into functions/shared/.`);
