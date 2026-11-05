// npm run deploy:functions — deploys the resetDemo callable (the apps' Demo reset
// button) to the hosted project in backend/.env (FIREBASE_PROJECT_ID).
import { readConfig, runCli } from '../lib/env.js';
import { deployResetDemo, RESET_DEMO_FUNCTION, RESET_DEMO_REGION } from '../lib/deploy.js';

await runCli(async () => {
  const config = readConfig(); // throws before anything connects if env is incomplete
  deployResetDemo(config);
  console.log(`Deployed: callable "${RESET_DEMO_FUNCTION}" in ${RESET_DEMO_REGION}, project ${config.projectId}.`);
});
