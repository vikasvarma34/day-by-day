import { getPool, closePool } from '../src/db/pool';
import { getNodeEnv, getEnv } from '../src/config/env';

export async function resetDevelopmentDatabase(): Promise<void> {
  const nodeEnv = getNodeEnv();
  const allowReset = process.env.ALLOW_DEV_DB_RESET?.trim();

  // Guard 1: Require NODE_ENV === "development" and ALLOW_DEV_DB_RESET === "true"
  if (nodeEnv !== 'development' || allowReset !== 'true') {
    throw new Error(
      `Database reset aborted: destructive development database reset requires NODE_ENV=development and ALLOW_DEV_DB_RESET=true (currently NODE_ENV="${nodeEnv}", ALLOW_DEV_DB_RESET="${allowReset ?? ''}").`
    );
  }

  // Guard 2: Secondary URL sanity check for production indicators
  const dbUrl = getEnv('DATABASE_URL').toLowerCase();
  const migrationDbUrl = getEnv('MIGRATION_DATABASE_URL').toLowerCase();

  if (dbUrl.includes('prod') || migrationDbUrl.includes('prod')) {
    throw new Error(
      'Database reset aborted: DATABASE_URL or MIGRATION_DATABASE_URL contains "prod" in the connection string.'
    );
  }

  console.log(`Resetting development database (NODE_ENV=${nodeEnv})...`);

  const pool = getPool();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Remove user-owned and runtime data in foreign-key safe order
    // Preserves schema, constraints, triggers, indexes, and pgmigrations table
    await client.query('DELETE FROM task_completions');
    await client.query('DELETE FROM task_schedules');
    await client.query('DELETE FROM tasks');
    await client.query('DELETE FROM auth_sessions');
    await client.query('DELETE FROM auth_throttles');
    await client.query('DELETE FROM users');

    await client.query('COMMIT');
    console.log('✓ Development database reset successfully. Preserved schema and migrations.');
  } catch (error: any) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('Database reset failed:', error.message || error);
    process.exitCode = 1;
  } finally {
    client.release();
    await closePool();
  }
}

if (require.main === module) {
  resetDevelopmentDatabase();
}
