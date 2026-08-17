import { Pool, PoolConfig } from 'pg';
import { attachDatabasePool } from '@vercel/functions';
import { getEnv } from '../config/env';
import { logger } from '../logging/logger';

export const DB_POOL_DEFAULTS = {
  max: 5,
  connectionTimeoutMillis: 5000,
  idleTimeoutMillis: 30000,
  statement_timeout: 10000,
  query_timeout: 10000,
  options: '-c lock_timeout=3000 -c idle_in_transaction_session_timeout=15000',
} as const;

let poolInstance: Pool | null = null;

export function createPoolConfig(connectionString: string): PoolConfig {
  return {
    connectionString,
    max: DB_POOL_DEFAULTS.max,
    connectionTimeoutMillis: DB_POOL_DEFAULTS.connectionTimeoutMillis,
    idleTimeoutMillis: DB_POOL_DEFAULTS.idleTimeoutMillis,
    statement_timeout: DB_POOL_DEFAULTS.statement_timeout,
    query_timeout: DB_POOL_DEFAULTS.query_timeout,
    options: DB_POOL_DEFAULTS.options,
  };
}

export function getPool(): Pool {
  if (!poolInstance) {
    const connectionString = getEnv('DATABASE_URL');
    poolInstance = new Pool(createPoolConfig(connectionString));

    // Prevent idle client errors from terminating the Node process
    poolInstance.on('error', (err: Error) => {
      logger.error('db_pool_idle_error', {
        errorType: err.name || 'Error',
      });
    });

    attachDatabasePool(poolInstance);
  }
  return poolInstance;
}

export async function closePool(): Promise<void> {
  if (poolInstance) {
    await poolInstance.end();
    poolInstance = null;
  }
}
