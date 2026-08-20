import { getPool } from '../../src/db/pool';
import { AuthUser } from '../../src/auth/types';
import { hashPassword } from '../../src/security/password';

export const AUTOMATED_TEST_EMAIL_DOMAIN = 'daybyday-test.invalid';

/**
 * Generates a unique automated test email strictly ending in @daybyday-test.invalid.
 */
export function generateAutomatedTestEmail(label: string): string {
  const cleanLabel = label.replace(/[^a-zA-Z0-9._-]/g, '');
  return `test.${Date.now()}.${Math.floor(Math.random() * 1000000)}.${cleanLabel}@${AUTOMATED_TEST_EMAIL_DOMAIN}`;
}

/**
 * Inserts an isolated test user with pre-hashed Argon2id password for integration test suites.
 */
export async function createTestUserFixture(data: {
  emailSuffix: string;
  rawPassword?: string;
  firstName?: string;
  lastName?: string;
  nickname?: string | null;
}): Promise<AuthUser & { rawPassword: string }> {
  const pool = getPool();
  const rawPassword = data.rawPassword ?? 'StrongPassword12345!';
  const email = generateAutomatedTestEmail(data.emailSuffix);
  const passwordHash = await hashPassword(rawPassword);

  const result = await pool.query(
    `INSERT INTO users (email, password_hash, first_name, last_name, nickname)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id, email, first_name, last_name, nickname`,
    [email, passwordHash, data.firstName ?? 'Integration', data.lastName ?? 'Tester', data.nickname ?? 'Tester']
  );

  const row = result.rows[0];
  return {
    id: row.id,
    email: row.email,
    firstName: row.first_name,
    lastName: row.last_name,
    nickname: row.nickname,
    rawPassword,
  };
}

/**
 * Cleans up a test user and all cascading session rows.
 */
export async function deleteTestUserById(userId: string): Promise<void> {
  const pool = getPool();
  await pool.query('DELETE FROM users WHERE id = $1', [userId]);
}

/**
 * Safely purges leftover automated test users and related planner/auth rows.
 * Only deletes users whose email strictly ends with @daybyday-test.invalid.
 */
export async function cleanAutomatedTestUsers(): Promise<{ deletedUserCount: number }> {
  const allowCleanup = process.env.ALLOW_AUTOMATED_TEST_USER_CLEANUP?.trim();
  if (allowCleanup !== 'true') {
    throw new Error(
      `Automated test user cleanup aborted: requires ALLOW_AUTOMATED_TEST_USER_CLEANUP=true (received "${allowCleanup ?? ''}").`
    );
  }

  const pool = getPool();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    const testDomainPattern = `%@${AUTOMATED_TEST_EMAIL_DOMAIN}`;

    // Clean child planner rows for test users
    await client.query(
      `DELETE FROM task_completions
       WHERE task_id IN (
         SELECT id FROM tasks WHERE user_id IN (
           SELECT id FROM users WHERE LOWER(email) LIKE $1
         )
       )`,
      [testDomainPattern]
    );

    await client.query(
      `DELETE FROM task_schedules
       WHERE task_id IN (
         SELECT id FROM tasks WHERE user_id IN (
           SELECT id FROM users WHERE LOWER(email) LIKE $1
         )
       )`,
      [testDomainPattern]
    );

    await client.query(
      `DELETE FROM tasks
       WHERE user_id IN (
         SELECT id FROM users WHERE LOWER(email) LIKE $1
       )`,
      [testDomainPattern]
    );

    await client.query(
      `DELETE FROM auth_throttles
       WHERE user_id IN (
         SELECT id FROM users WHERE LOWER(email) LIKE $1
       )`,
      [testDomainPattern]
    );

    await client.query(
      `DELETE FROM auth_sessions
       WHERE user_id IN (
         SELECT id FROM users WHERE LOWER(email) LIKE $1
       )`,
      [testDomainPattern]
    );

    const result = await client.query(
      `DELETE FROM users
       WHERE LOWER(email) LIKE $1
       RETURNING id`,
      [testDomainPattern]
    );

    await client.query('COMMIT');
    return { deletedUserCount: result.rowCount ?? 0 };
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}
