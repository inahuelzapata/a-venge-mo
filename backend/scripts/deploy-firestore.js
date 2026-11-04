// npm run deploy:firestore — pushes the repo's security rules and composite
// indexes to the hosted project in backend/.env (FIREBASE_PROJECT_ID).
import { readConfig, runCli } from '../lib/env.js';
import { deployRulesAndIndexes } from '../lib/deploy.js';

await runCli(async () => {
  const config = readConfig(); // throws before anything connects if env is incomplete
  deployRulesAndIndexes(config);
  console.log('Rules and indexes deployed. New indexes can take a few minutes to finish building (Firebase console > Firestore > Indexes).');
});
