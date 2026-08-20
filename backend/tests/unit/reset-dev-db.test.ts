import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resetDevelopmentDatabase } from '../../scripts/reset-dev-db';

test('Development Database Reset Safety Guards Unit Tests', async (t) => {
  await t.test('aborts with error if ALLOW_DEV_DB_RESET is missing or false', async () => {
    const originalResetFlag = process.env.ALLOW_DEV_DB_RESET;
    const originalNodeEnv = process.env.NODE_ENV;

    try {
      process.env.NODE_ENV = 'development';
      process.env.ALLOW_DEV_DB_RESET = 'false';

      await assert.rejects(
        () => resetDevelopmentDatabase(),
        (err: Error) => {
          assert.match(err.message, /Database reset aborted.*ALLOW_DEV_DB_RESET=true/);
          return true;
        }
      );

      delete process.env.ALLOW_DEV_DB_RESET;
      await assert.rejects(
        () => resetDevelopmentDatabase(),
        (err: Error) => {
          assert.match(err.message, /Database reset aborted.*ALLOW_DEV_DB_RESET=true/);
          return true;
        }
      );
    } finally {
      if (originalResetFlag !== undefined) {
        process.env.ALLOW_DEV_DB_RESET = originalResetFlag;
      } else {
        delete process.env.ALLOW_DEV_DB_RESET;
      }
      if (originalNodeEnv !== undefined) {
        process.env.NODE_ENV = originalNodeEnv;
      } else {
        delete process.env.NODE_ENV;
      }
    }
  });

  await t.test('aborts with error if NODE_ENV is test or production even with ALLOW_DEV_DB_RESET=true', async () => {
    const originalResetFlag = process.env.ALLOW_DEV_DB_RESET;
    const originalNodeEnv = process.env.NODE_ENV;

    try {
      process.env.ALLOW_DEV_DB_RESET = 'true';

      process.env.NODE_ENV = 'test';
      await assert.rejects(
        () => resetDevelopmentDatabase(),
        (err: Error) => {
          assert.match(err.message, /Database reset aborted.*NODE_ENV=development/);
          return true;
        }
      );

      process.env.NODE_ENV = 'production';
      await assert.rejects(
        () => resetDevelopmentDatabase(),
        (err: Error) => {
          assert.match(err.message, /Database reset aborted.*NODE_ENV=development/);
          return true;
        }
      );
    } finally {
      if (originalResetFlag !== undefined) {
        process.env.ALLOW_DEV_DB_RESET = originalResetFlag;
      } else {
        delete process.env.ALLOW_DEV_DB_RESET;
      }
      if (originalNodeEnv !== undefined) {
        process.env.NODE_ENV = originalNodeEnv;
      } else {
        delete process.env.NODE_ENV;
      }
    }
  });

  await t.test('proves reset-dev-db script never executes DELETE FROM users or TRUNCATE users', async () => {
    const fs = await import('node:fs');
    const path = await import('node:path');
    const scriptPath = path.resolve(__dirname, '../../scripts/reset-dev-db.ts');
    const content = fs.readFileSync(scriptPath, 'utf8');

    assert.ok(
      !content.includes('DELETE FROM users'),
      'reset-dev-db.ts must not contain DELETE FROM users'
    );
    assert.ok(
      !content.includes('TRUNCATE'),
      'reset-dev-db.ts must not contain TRUNCATE'
    );
    assert.ok(
      content.includes('DELETE FROM task_completions'),
      'reset-dev-db.ts must clear task_completions'
    );
    assert.ok(
      content.includes('DELETE FROM task_schedules'),
      'reset-dev-db.ts must clear task_schedules'
    );
    assert.ok(
      content.includes('DELETE FROM tasks'),
      'reset-dev-db.ts must clear tasks'
    );
    assert.ok(
      content.includes('DELETE FROM auth_sessions'),
      'reset-dev-db.ts must clear auth_sessions'
    );
    assert.ok(
      content.includes('DELETE FROM auth_throttles'),
      'reset-dev-db.ts must clear auth_throttles'
    );
    assert.ok(
      content.includes('findUserByEmail(devEmail)'),
      'reset-dev-db.ts must check if configured dev user exists'
    );
  });
});

