import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  AUTOMATED_TEST_EMAIL_DOMAIN,
  generateAutomatedTestEmail,
  cleanAutomatedTestUsers,
} from '../integration/auth-test-fixtures';

test('Automated Test User Isolation & Cleanup Unit Suite', async (t) => {
  await t.test('1. generated automated test email ends exactly with @daybyday-test.invalid', () => {
    const email = generateAutomatedTestEmail('sample');
    assert.ok(
      email.endsWith(`@${AUTOMATED_TEST_EMAIL_DOMAIN}`),
      `Email "${email}" should end with "@${AUTOMATED_TEST_EMAIL_DOMAIN}"`
    );
    assert.equal(AUTOMATED_TEST_EMAIL_DOMAIN, 'daybyday-test.invalid');
  });

  await t.test('2. two generated test users receive different emails', () => {
    const email1 = generateAutomatedTestEmail('userA');
    const email2 = generateAutomatedTestEmail('userB');
    assert.notEqual(email1, email2);
  });

  await t.test('3 & 4. predicate matches single and multiple @daybyday-test.invalid emails', () => {
    const pattern = new RegExp(`@${AUTOMATED_TEST_EMAIL_DOMAIN.replace('.', '\\.')}$`, 'i');

    const testEmail1 = generateAutomatedTestEmail('batch1');
    const testEmail2 = generateAutomatedTestEmail('batch2');
    const testEmail3 = 'custom.prefix@daybyday-test.invalid';

    assert.ok(pattern.test(testEmail1));
    assert.ok(pattern.test(testEmail2));
    assert.ok(pattern.test(testEmail3));
  });

  await t.test('5, 6, 7. predicate strictly preserves dev user, normal emails, and tricky subdomain/suffix variants', () => {
    const pattern = new RegExp(`@${AUTOMATED_TEST_EMAIL_DOMAIN.replace('.', '\\.')}$`, 'i');

    const devEmail = 'dev.user@example.com';
    const normalUser = 'alice.developer@gmail.com';
    const trickyDomain1 = 'user@not-daybyday-test.invalid-example.com';
    const trickyDomain2 = 'user@daybyday-test.invalid.attacker.com';
    const trickyDomain3 = 'user@sub.daybyday-test.invalid.org';

    assert.equal(pattern.test(devEmail), false, 'Must not match dev user email');
    assert.equal(pattern.test(normalUser), false, 'Must not match normal user email');
    assert.equal(pattern.test(trickyDomain1), false, 'Must not match tricky suffix');
    assert.equal(pattern.test(trickyDomain2), false, 'Must not match domain as subdomain prefix');
    assert.equal(pattern.test(trickyDomain3), false, 'Must not match non-.invalid TLD');
  });

  await t.test('8. cleanup execution is idempotent, scopes deletes to @daybyday-test.invalid, and wraps in transaction', async () => {
    const originalGuard = process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP;
    process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP = 'true';

    try {
      const executedQueries: Array<{ text: string; values?: readonly unknown[] }> = [];
      let clientReleased = false;

      const createFakePool = () => ({
        connect: async () => ({
          query: async (text: string, values?: readonly unknown[]) => {
            executedQueries.push({ text: text.trim(), values });
            if (text.includes('DELETE FROM users')) {
              return { rowCount: 2 };
            }
            return { rowCount: 0 };
          },
          release: () => {
            clientReleased = true;
          },
        }),
      });

      // 1. Run cleanup
      const result1 = await cleanAutomatedTestUsers({ pool: createFakePool() });
      assert.equal(result1.deletedUserCount, 2);
      assert.equal(clientReleased, true);

      // Verify transaction boundary
      assert.equal(executedQueries[0].text, 'BEGIN');
      assert.equal(executedQueries[executedQueries.length - 1].text, 'COMMIT');

      // Verify all DELETE queries are scoped to the testDomainPattern parameter
      const deleteQueries = executedQueries.filter((q) => q.text.startsWith('DELETE FROM'));
      assert.equal(deleteQueries.length, 6, 'Should execute 6 scoped deletes (completions, schedules, tasks, throttles, sessions, users)');

      for (const q of deleteQueries) {
        assert.ok(
          q.values && q.values[0] === '%@daybyday-test.invalid',
          `Delete query must be parameterized with test domain pattern: ${q.text}`
        );
        assert.ok(
          !q.text.includes('DELETE FROM users;') && !q.text.includes('TRUNCATE'),
          'Must never issue unconditional DELETE or TRUNCATE on users'
        );
      }

      // 2. Idempotency: run cleanup a second time
      executedQueries.length = 0;
      const result2 = await cleanAutomatedTestUsers({ pool: createFakePool() });
      assert.equal(result2.deletedUserCount, 2);
      assert.equal(executedQueries[0].text, 'BEGIN');
      assert.equal(executedQueries[executedQueries.length - 1].text, 'COMMIT');

      // 3. Rollback on query failure
      let rollbackCalled = false;
      const failingPool = {
        connect: async () => ({
          query: async (text: string) => {
            if (text === 'BEGIN') return {};
            if (text === 'ROLLBACK') {
              rollbackCalled = true;
              return {};
            }
            throw new Error('database disk failure');
          },
          release: () => {},
        }),
      };

      await assert.rejects(
        () => cleanAutomatedTestUsers({ pool: failingPool }),
        /database disk failure/
      );
      assert.equal(rollbackCalled, true, 'Must execute ROLLBACK when query fails');
    } finally {
      if (originalGuard !== undefined) {
        process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP = originalGuard;
      } else {
        delete process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP;
      }
    }
  });

  await t.test('9. cleanup refuses to run without its explicit safety guard ALLOW_AUTOMATED_TEST_USER_CLEANUP=true', async () => {
    const originalGuard = process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP;
    try {
      delete process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP;
      await assert.rejects(
        () => cleanAutomatedTestUsers(),
        (err: Error) => {
          assert.match(
            err.message,
            /Automated test user cleanup aborted: requires ALLOW_AUTOMATED_TEST_USER_CLEANUP=true/
          );
          return true;
        }
      );

      process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP = 'false';
      await assert.rejects(
        () => cleanAutomatedTestUsers(),
        (err: Error) => {
          assert.match(
            err.message,
            /Automated test user cleanup aborted: requires ALLOW_AUTOMATED_TEST_USER_CLEANUP=true/
          );
          return true;
        }
      );
    } finally {
      if (originalGuard !== undefined) {
        process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP = originalGuard;
      } else {
        delete process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP;
      }
    }
  });

  await t.test('10. multiple independent test emails can be generated concurrently with unique values', () => {
    const emails = Array.from({ length: 50 }, (_, i) => generateAutomatedTestEmail(`concurrent_${i}`));
    const uniqueEmails = new Set(emails);
    assert.equal(uniqueEmails.size, 50, 'All 50 generated emails must be unique');
  });
});
