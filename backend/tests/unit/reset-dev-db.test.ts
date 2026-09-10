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

  await t.test('proves reset-dev-db execution deletes runtime planner/session data but never deletes or truncates users', async () => {
    const originalResetFlag = process.env.ALLOW_DEV_DB_RESET;
    const originalNodeEnv = process.env.NODE_ENV;
    const originalEmail = process.env.TEST_USER_EMAIL;
    const originalPassword = process.env.TEST_USER_PASSWORD;
    const originalDbUrl = process.env.DATABASE_URL;
    const originalMigDbUrl = process.env.MIGRATION_DATABASE_URL;

    try {
      process.env.NODE_ENV = 'development';
      process.env.ALLOW_DEV_DB_RESET = 'true';
      process.env.TEST_USER_EMAIL = 'dev.user@example.com';
      process.env.TEST_USER_PASSWORD = 'DevPassword12345!';
      process.env.DATABASE_URL = 'postgresql://user:pass@localhost:5432/daybyday_dev';
      process.env.MIGRATION_DATABASE_URL = 'postgresql://user:pass@localhost:5432/daybyday_dev';

      const executedQueries: string[] = [];
      let clientReleased = false;

      const fakePool = {
        connect: async () => ({
          query: async (text: string) => {
            executedQueries.push(text.trim());
            return { rows: [], rowCount: 0 };
          },
          release: () => {
            clientReleased = true;
          },
        }),
      };

      let findUserCalledWith: string | null = null;
      let createUserCalled = false;
      const fakeAuthRepo = {
        findUserByEmail: async (email: string) => {
          findUserCalledWith = email;
          return { id: 'dev-1', email, firstName: 'Dev', lastName: 'User', nickname: null } as any;
        },
      };

      await resetDevelopmentDatabase({
        pool: fakePool,
        authRepository: fakeAuthRepo,
        createUser: async () => {
          createUserCalled = true;
          return {} as any;
        },
      });

      assert.equal(clientReleased, true);

      // Verify transaction boundary
      assert.equal(executedQueries[0], 'BEGIN');
      assert.equal(executedQueries[executedQueries.length - 1], 'COMMIT');

      // Verify required table cleanups
      assert.ok(executedQueries.includes('DELETE FROM task_completions'), 'Must delete task_completions');
      assert.ok(executedQueries.includes('DELETE FROM task_schedules'), 'Must delete task_schedules');
      assert.ok(executedQueries.includes('DELETE FROM tasks'), 'Must delete tasks');
      assert.ok(executedQueries.includes('DELETE FROM auth_sessions'), 'Must delete auth_sessions');
      assert.ok(executedQueries.includes('DELETE FROM auth_throttles'), 'Must delete auth_throttles');

      // Strictly verify no query contains DELETE FROM users or TRUNCATE
      for (const query of executedQueries) {
        assert.ok(!query.includes('DELETE FROM users'), `Query must not delete from users table: ${query}`);
        assert.ok(!query.toUpperCase().includes('TRUNCATE'), `Query must not truncate any table: ${query}`);
      }

      // Verify persistent dev user confirmation
      assert.equal(findUserCalledWith, 'dev.user@example.com');
      assert.equal(createUserCalled, false, 'Should not seed dev user when user already exists');

      // Now verify seeding when user does NOT exist
      let seedUserCalled = false;
      let seedEmailPassed = '';
      const missingUserRepo = {
        findUserByEmail: async () => null,
      };

      await resetDevelopmentDatabase({
        pool: fakePool,
        authRepository: missingUserRepo,
        createUser: async (data: any) => {
          seedUserCalled = true;
          seedEmailPassed = data.email;
          return { id: 'new-dev-id', email: data.email } as any;
        },
      });

      assert.equal(seedUserCalled, true, 'Should seed dev user when user is missing');
      assert.equal(seedEmailPassed, 'dev.user@example.com');

      // Verify transaction rollback on query failure
      let rollbackExecuted = false;
      const failingPool = {
        connect: async () => ({
          query: async (text: string) => {
            if (text === 'BEGIN') return {};
            if (text === 'ROLLBACK') {
              rollbackExecuted = true;
              return {};
            }
            throw new Error('connection lost');
          },
          release: () => {},
        }),
      };

      await assert.rejects(
        () => resetDevelopmentDatabase({ pool: failingPool }),
        /connection lost/
      );
      assert.equal(rollbackExecuted, true, 'Must execute ROLLBACK when query fails');
    } finally {
      if (originalResetFlag !== undefined) process.env.ALLOW_DEV_DB_RESET = originalResetFlag;
      else delete process.env.ALLOW_DEV_DB_RESET;
      if (originalNodeEnv !== undefined) process.env.NODE_ENV = originalNodeEnv;
      else delete process.env.NODE_ENV;
      if (originalEmail !== undefined) process.env.TEST_USER_EMAIL = originalEmail;
      else delete process.env.TEST_USER_EMAIL;
      if (originalPassword !== undefined) process.env.TEST_USER_PASSWORD = originalPassword;
      else delete process.env.TEST_USER_PASSWORD;
      if (originalDbUrl !== undefined) process.env.DATABASE_URL = originalDbUrl;
      else delete process.env.DATABASE_URL;
      if (originalMigDbUrl !== undefined) process.env.MIGRATION_DATABASE_URL = originalMigDbUrl;
      else delete process.env.MIGRATION_DATABASE_URL;
    }
  });
});

