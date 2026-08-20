import { cleanAutomatedTestUsers } from '../tests/integration/auth-test-fixtures';
import { closePool } from '../src/db/pool';

async function main() {
  try {
    const result = await cleanAutomatedTestUsers();
    if (result.deletedUserCount > 0) {
      console.log(`✓ Cleaned ${result.deletedUserCount} leftover @daybyday-test.invalid test users.`);
    }
  } catch (err: any) {
    console.error('Failed to clean automated test users:', err.message || err);
    process.exitCode = 1;
  } finally {
    await closePool();
  }
}

if (require.main === module) {
  main();
}
