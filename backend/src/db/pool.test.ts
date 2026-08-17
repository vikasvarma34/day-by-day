import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getPool, closePool, createPoolConfig, DB_POOL_DEFAULTS } from './pool';

test('createPoolConfig configures finite connection, query, lock, and transaction limits', () => {
  const dummyUrl = 'postgresql://user:pass@localhost:5432/mydb';
  const config = createPoolConfig(dummyUrl);

  assert.equal(config.connectionString, dummyUrl);
  assert.equal(config.max, 5);
  assert.equal(config.connectionTimeoutMillis, 5000);
  assert.equal(config.idleTimeoutMillis, 30000);
  assert.equal(config.statement_timeout, 10000);
  assert.equal(config.query_timeout, 10000);
  assert.equal(config.options, DB_POOL_DEFAULTS.options);
  assert.match(config.options as string, /lock_timeout=3000/);
  assert.match(config.options as string, /idle_in_transaction_session_timeout=15000/);
});

test('getPool throws if DATABASE_URL is missing and closes cleanly', async () => {
  const oldUrl = process.env.DATABASE_URL;
  delete process.env.DATABASE_URL;
  await closePool();
  try {
    assert.throws(
      () => getPool(),
      (err: Error) => {
        assert.match(err.message, /DATABASE_URL/);
        return true;
      }
    );
  } finally {
    if (oldUrl !== undefined) {
      process.env.DATABASE_URL = oldUrl;
    }
    await closePool();
  }
});
