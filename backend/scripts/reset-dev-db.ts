import { getPool, closePool } from '../src/db/pool';
import { getNodeEnv, getEnv } from '../src/config/env';
import { normalizeEmail } from '../src/security/validation';
import { createUserAccount } from '../src/auth/user-creation';
import { AuthRepository } from '../src/auth/auth.repository';

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

  const devEmail = normalizeEmail(getEnv('TEST_USER_EMAIL'));
  const devPassword = getEnv('TEST_USER_PASSWORD');

  console.log(`Resetting development database (NODE_ENV=${nodeEnv})...`);

  const pool = getPool();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Remove user-owned and runtime data in foreign-key safe order
    // Preserves schema, constraints, triggers, indexes, pgmigrations table, and all existing users
    await client.query('DELETE FROM task_completions');
    await client.query('DELETE FROM task_schedules');
    await client.query('DELETE FROM tasks');
    await client.query('DELETE FROM auth_sessions');
    await client.query('DELETE FROM auth_throttles');

    await client.query('COMMIT');

    // Ensure the configured persistent dev user exists; if missing, seed it
    const repository = new AuthRepository();
    const existing = await repository.findUserByEmail(devEmail);
    if (!existing) {
      const created = await createUserAccount({
        email: devEmail,
        password: devPassword,
        firstName: 'Dev',
        lastName: 'Tester',
        nickname: 'Dev',
      });
      console.log(`✓ Development user seeded: ${created.email} (ID: ${created.id}).`);
    } else {
      console.log(`✓ Persistent development user confirmed: ${existing.email} (ID: ${existing.id}).`);
    }

    console.log('✓ Development database reset successfully. Preserved schema, migrations, and all users.');
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
