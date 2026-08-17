import { Pool } from 'pg';
import { getPool, closePool } from '../src/db/pool';
import { getEnv } from '../src/config/env';

async function checkDatabaseConnectivity() {
  let appDbSuccess = false;
  let migrationDbSuccess = false;

  // 1. Check DATABASE_URL
  try {
    const pool = getPool();
    const result = await pool.query('SELECT 1 as connected');
    if (result.rows.length > 0 && result.rows[0].connected === 1) {
      console.log('DATABASE_URL connectivity check succeeded.');
      appDbSuccess = true;
    } else {
      console.log('DATABASE_URL returned unexpected result:', result.rows);
    }
  } catch (error: any) {
    console.error('DATABASE_URL connectivity check failed:', error.message || error);
  } finally {
    await closePool();
  }

  // 2. Check MIGRATION_DATABASE_URL
  let migrationPool: Pool | null = null;
  try {
    const migrationUrl = getEnv('MIGRATION_DATABASE_URL');
    migrationPool = new Pool({ connectionString: migrationUrl });
    const result = await migrationPool.query('SELECT 1 as connected');
    if (result.rows.length > 0 && result.rows[0].connected === 1) {
      console.log('MIGRATION_DATABASE_URL connectivity check succeeded.');
      migrationDbSuccess = true;
    } else {
      console.log('MIGRATION_DATABASE_URL returned unexpected result:', result.rows);
    }
  } catch (error: any) {
    console.error('MIGRATION_DATABASE_URL connectivity check failed:', error.message || error);
  } finally {
    if (migrationPool) {
      await migrationPool.end();
    }
  }

  if (!appDbSuccess || !migrationDbSuccess) {
    process.exitCode = 1;
  }
}

checkDatabaseConnectivity();
