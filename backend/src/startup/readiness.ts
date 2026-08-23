import { readdirSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { getEnv } from '../config/env';
import { closePool, getPool } from '../db/pool';

export class StartupError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'StartupError';
  }
}

export interface ReadinessQueryClient {
  query<T extends Record<string, unknown> = Record<string, unknown>>(
    text: string,
    values?: readonly unknown[]
  ): Promise<{ rows: T[] }>;
}

export const DEFAULT_MIGRATIONS_DIRECTORY = resolve(__dirname, '../../migrations');

export function validateBootConfiguration(environment: NodeJS.ProcessEnv = process.env): void {
  if (!environment.DATABASE_URL || environment.DATABASE_URL.trim() === '') {
    throw new StartupError('Startup failed: missing required environment variable DATABASE_URL');
  }
}

export function getMigrationNames(migrationsDirectory: string = DEFAULT_MIGRATIONS_DIRECTORY): string[] {
  return readdirSync(migrationsDirectory)
    .filter((fileName) => fileName.endsWith('.sql'))
    .map((fileName) => basename(fileName, '.sql'))
    .sort();
}

export async function checkDatabaseReadiness(options: {
  client?: ReadinessQueryClient;
  migrationsDirectory?: string;
} = {}): Promise<void> {
  const client = options.client ?? (getPool() as unknown as ReadinessQueryClient);

  try {
    await client.query('SELECT 1 AS connected');
  } catch {
    throw new StartupError('Startup failed: database readiness check failed');
  }

  let expectedMigrations: string[];
  try {
    expectedMigrations = getMigrationNames(options.migrationsDirectory);
    const result = await client.query<{ name: string }>('SELECT name FROM pgmigrations ORDER BY id');
    const appliedMigrations = new Set(result.rows.map((row) => row.name));
    const missingMigrations = expectedMigrations.filter((name) => !appliedMigrations.has(name));

    if (missingMigrations.length > 0) {
      throw new StartupError('Startup failed: database schema is not ready');
    }
  } catch (error) {
    if (error instanceof StartupError) {
      throw error;
    }
    throw new StartupError('Startup failed: database schema is not ready');
  }
}

export async function closeStartupDatabase(): Promise<void> {
  await closePool();
}
