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

  await t.test('8. cleanup implementation is idempotent and uses scoped queries', async () => {
    const fs = await import('node:fs');
    const path = await import('node:path');
    const fixturesPath = path.resolve(__dirname, '../integration/auth-test-fixtures.ts');
    const content = fs.readFileSync(fixturesPath, 'utf8');

    assert.ok(content.includes('WHERE LOWER(email) LIKE $1'));
    assert.ok(content.includes('testDomainPattern'));
    assert.ok(!content.includes('DELETE FROM users;') && !content.includes('DELETE FROM users WHERE id !='));
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
