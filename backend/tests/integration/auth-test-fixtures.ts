import { getPool } from '../../src/db/pool';
import { AuthUser } from '../../src/auth/types';
import { hashPassword } from '../../src/security/password';

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
  const email = `test.user.${Date.now()}.${Math.floor(Math.random() * 100000)}.${data.emailSuffix}@example.com`;
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
