// npm run deploy:reset-endpoint — deploys the free Demo reset endpoint
// (reset-endpoint/, Vercel) for the hosted project in backend/.env, as the
// alternative to `npm run deploy:functions` when the project is not on Blaze.
//
// 1. copies lib/ into reset-endpoint/shared/ (scripts/build-functions.js)
// 2. links reset-endpoint/ to the Vercel project "<FIREBASE_PROJECT_ID>-demo-reset"
// 3. sets RESET_PROJECT_ID / RESET_CLIENT_EMAIL / RESET_PRIVATE_KEY (production)
//    from the service-account key; the values are piped, never printed
// 4. deploys to production and checks the URL answers a GET with 400
// Prints the URL to put in the apps' RESET_DEMO_URL.
// Needs `npx vercel login` once on this machine.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { BACKEND_DIR, CliError, ConfigError, readConfig, runCli } from '../lib/env.js';

const VERCEL = ['--yes', 'vercel@65.11.0'];
const ENDPOINT_DIR = join(BACKEND_DIR, 'reset-endpoint');
const ENDPOINT_PATH = '/api/resetDemo';

function vercel(args, { input, capture = false } = {}) {
  const r = spawnSync('npx', [...VERCEL, ...args], {
    cwd: ENDPOINT_DIR,
    input,
    encoding: 'utf8',
    stdio: [input === undefined ? 'inherit' : 'pipe', capture ? 'pipe' : 'inherit', capture ? 'pipe' : 'inherit'],
  });
  if (r.error) throw new CliError(`Could not start the Vercel CLI (${r.error.message}).`);
  return r;
}

function mustSucceed(r, what) {
  if (r.status !== 0) {
    throw new CliError(`${what} failed (exit ${r.status}).${r.stderr ? `\n${r.stderr}` : ''}\n  Not logged in? Run: npx vercel login`);
  }
  return r;
}

await runCli(async () => {
  const config = readConfig(); // throws before anything is sent if env is incomplete
  if (config.target !== 'hosted') {
    throw new ConfigError(`The reset endpoint resets the hosted project only, but FIREBASE_TARGET=${config.target}. Nothing was deployed.`);
  }
  if (config.credentials?.kind !== 'key') {
    throw new ConfigError(
      'The reset endpoint needs a service-account key (GOOGLE_APPLICATION_CREDENTIALS=service-account.json): ' +
        'application-default credentials are personal and cannot be uploaded. Nothing was deployed.',
    );
  }
  const key = JSON.parse(readFileSync(config.credentials.path, 'utf8'));
  const vars = {
    RESET_PROJECT_ID: config.projectId,
    RESET_CLIENT_EMAIL: key.client_email,
    RESET_PRIVATE_KEY: key.private_key.replace(/\n/g, '\\n'), // one line; the endpoint restores the newlines
  };

  mustSucceed(spawnSync(process.execPath, [join(BACKEND_DIR, 'scripts', 'build-functions.js')], { stdio: 'inherit' }), 'Copying lib/');

  const project = `${config.projectId}-demo-reset`;
  console.log(`Linking reset-endpoint/ to Vercel project ${project} ...`);
  mustSucceed(vercel(['link', '--yes', '--project', project]), 'vercel link');
  // `vercel link` also tries to connect the GitHub repo; a connected repo would
  // deploy (and fail to build) on every push, so keep the project CLI-only.
  vercel(['git', 'disconnect', '--yes'], { capture: true });

  for (const [name, value] of Object.entries(vars)) {
    vercel(['env', 'rm', name, 'production', '--yes'], { capture: true }); // absent the first time
    mustSucceed(vercel(['env', 'add', name, 'production'], { input: value, capture: true }), `Setting ${name}`);
    console.log(`Set ${name} (production).`);
  }

  console.log(`Deploying the Demo reset endpoint for HOSTED project ${config.projectId} ...`);
  const deploy = mustSucceed(vercel(['deploy', '--prod', '--yes'], { capture: true }), 'vercel deploy');
  const output = `${deploy.stdout}\n${deploy.stderr}`;
  // The production alias (public), not the per-deployment URL (behind Vercel's deployment protection).
  const alias = output.match(/Aliased:?\s+(https:\/\/[\w.-]+\.vercel\.app)/)?.[1] ?? `https://${project}.vercel.app`;
  const url = `${alias}${ENDPOINT_PATH}`;

  const probe = await fetch(url).catch((e) => ({ status: `unreachable (${e.message})` }));
  if (probe.status !== 400) {
    throw new CliError(`Deployed, but GET ${url} answered ${probe.status} instead of 400. Vercel output:\n${output}`);
  }
  console.log(`\nDeployed: ${url}  (GET answers 400, as a callable should)`);
  console.log(`Put it in android/firestore.properties:  RESET_DEMO_URL=${url}`);
});
