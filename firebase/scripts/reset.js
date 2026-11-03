// npm run reset — deletes all parties and party data, restores the seeded state.
// Against hosted Firestore it requires --yes, because it deletes data.
import { describeTarget, openFirestore, readConfig, runCli } from '../lib/env.js';

await runCli(async () => {
  const config = readConfig(); // throws before anything connects if env is incomplete
  if (config.target === 'hosted' && !process.argv.includes('--yes')) {
    console.error(`Refusing to reset ${describeTarget(config)} without confirmation. Re-run: npm run reset -- --yes`);
    process.exitCode = 2;
    return;
  }
  const db = await openFirestore(config);
  const { reset } = await import('../lib/ops.js');
  console.log(`Resetting ${describeTarget(config)} ...`);
  const { removed, seeded } = await reset(db);
  console.log(
    `Removed: ${removed.parties} parties, ${removed.joinCodes} join-code locks, ` +
      `${removed.transactions} non-seed transactions, ${removed.users} extra users, ${removed.merchants} extra merchants.`,
  );
  console.log(`Restored: ${seeded.users} users, ${seeded.merchants} merchant, ${seeded.transactions} feed transactions.`);
});
