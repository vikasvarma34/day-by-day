import { getPool } from '../db/pool';
import { AuthSession, AuthThrottle, AuthUser, UserWithPasswordHash } from './types';

export class AuthRepository {
  /**
   * Finds a user by their normalized email address.
   * Includes the password hash for internal verification.
   */
  async findUserByEmail(email: string): Promise<UserWithPasswordHash | null> {
    const pool = getPool();
    const result = await pool.query(
      `SELECT id, email, password_hash, first_name, last_name, nickname
       FROM users
       WHERE email = $1`,
      [email]
    );

    if (result.rows.length === 0) {
      return null;
    }

    const row = result.rows[0];
    return {
      id: row.id,
      email: row.email,
      passwordHash: row.password_hash,
      firstName: row.first_name,
      lastName: row.last_name,
      nickname: row.nickname,
    };
  }

  /**
   * Creates a new session record with hashed token and expiry timestamp.
   */
  async createSession(userId: string, tokenHash: string, expiresAt: Date): Promise<AuthSession> {
    const pool = getPool();
    const result = await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at)
       VALUES ($1, $2, $3)
       RETURNING id, user_id, token_hash, created_at, expires_at`,
      [userId, tokenHash, expiresAt]
    );

    const row = result.rows[0];
    return {
      id: row.id,
      userId: row.user_id,
      tokenHash: row.token_hash,
      createdAt: row.created_at,
      expiresAt: row.expires_at,
    };
  }

  /**
   * Resolves an authenticated user from a non-expired session token hash.
   */
  async findValidSessionAndUser(tokenHash: string, now: Date = new Date()): Promise<AuthUser | null> {
    const pool = getPool();
    const result = await pool.query(
      `SELECT u.id, u.email, u.first_name, u.last_name, u.nickname
       FROM auth_sessions s
       JOIN users u ON s.user_id = u.id
       WHERE s.token_hash = $1 AND s.expires_at > $2`,
      [tokenHash, now]
    );

    if (result.rows.length === 0) {
      return null;
    }

    const row = result.rows[0];
    return {
      id: row.id,
      email: row.email,
      firstName: row.first_name,
      lastName: row.last_name,
      nickname: row.nickname,
    };
  }

  /**
   * Deletes a session by its token hash (logout).
   * Returns true if a session was deleted, false if no session matched.
   */
  async deleteSessionByTokenHash(tokenHash: string): Promise<boolean> {
    const pool = getPool();
    const result = await pool.query(
      `DELETE FROM auth_sessions
       WHERE token_hash = $1`,
      [tokenHash]
    );

    return (result.rowCount ?? 0) > 0;
  }

  /**
   * Finds active throttle record if user account is currently blocked.
   */
  async findActiveThrottle(userId: string, now: Date = new Date()): Promise<AuthThrottle | null> {
    const pool = getPool();
    const result = await pool.query(
      `SELECT user_id, failed_attempts, window_started_at, blocked_until, updated_at
       FROM auth_throttles
       WHERE user_id = $1 AND blocked_until > $2`,
      [userId, now]
    );

    if (result.rows.length === 0) {
      return null;
    }

    const row = result.rows[0];
    return {
      userId: row.user_id,
      failedAttempts: row.failed_attempts,
      windowStartedAt: row.window_started_at,
      blockedUntil: row.blocked_until,
      updatedAt: row.updated_at,
    };
  }

  /**
   * Atomically records a failed password attempt in PostgreSQL.
   * Starts a 15-minute window or resets if previous window elapsed.
   * Sets blocked_until to now + 15 minutes upon reaching 5 attempts.
   */
  async recordFailedLogin(userId: string, now: Date = new Date()): Promise<AuthThrottle> {
    const pool = getPool();
    const result = await pool.query(
      `INSERT INTO auth_throttles (user_id, failed_attempts, window_started_at, blocked_until, updated_at)
       VALUES ($1, 1, $2, NULL, $2)
       ON CONFLICT (user_id) DO UPDATE SET
         failed_attempts = CASE
           WHEN auth_throttles.blocked_until > EXCLUDED.updated_at THEN auth_throttles.failed_attempts
           WHEN auth_throttles.window_started_at + INTERVAL '15 minutes' <= EXCLUDED.updated_at THEN 1
           ELSE auth_throttles.failed_attempts + 1
         END,
         window_started_at = CASE
           WHEN auth_throttles.blocked_until > EXCLUDED.updated_at THEN auth_throttles.window_started_at
           WHEN auth_throttles.window_started_at + INTERVAL '15 minutes' <= EXCLUDED.updated_at THEN EXCLUDED.updated_at
           ELSE auth_throttles.window_started_at
         END,
         blocked_until = CASE
           WHEN auth_throttles.blocked_until > EXCLUDED.updated_at THEN auth_throttles.blocked_until
           WHEN (
             CASE
               WHEN auth_throttles.window_started_at + INTERVAL '15 minutes' <= EXCLUDED.updated_at THEN 1
               ELSE auth_throttles.failed_attempts + 1
             END
           ) >= 5 THEN EXCLUDED.updated_at + INTERVAL '15 minutes'
           ELSE NULL
         END,
         updated_at = EXCLUDED.updated_at
       RETURNING user_id, failed_attempts, window_started_at, blocked_until, updated_at`,
      [userId, now]
    );

    const row = result.rows[0];
    return {
      userId: row.user_id,
      failedAttempts: row.failed_attempts,
      windowStartedAt: row.window_started_at,
      blockedUntil: row.blocked_until,
      updatedAt: row.updated_at,
    };
  }

  /**
   * Clears failed login counter and unblocks throttle state on successful login.
   */
  async resetThrottle(userId: string, now: Date = new Date()): Promise<void> {
    const pool = getPool();
    await pool.query(
      `UPDATE auth_throttles
       SET failed_attempts = 0, blocked_until = NULL, updated_at = $2
       WHERE user_id = $1`,
      [userId, now]
    );
  }
}
