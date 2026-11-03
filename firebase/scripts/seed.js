// npm run seed — creates/refreshes the 3 demo friends, Tony's, and the seeded feed.
import { describeTarget, openFirestore, readConfig, runCli } from '../lib/env.js';

await runCli(async () => {
  const config = readConfig(); // throws before anything connects if env is incomplete
  const db = await openFirestore(config);
  const { seed } = await import('../lib/ops.js');
  console.log(`Seeding ${describeTarget(config)} ...`);
  const counts = await seed(db);
  console.log(`Done: ${counts.users} users, ${counts.merchants} merchant, ${counts.transactions} feed transactions.`);
});
