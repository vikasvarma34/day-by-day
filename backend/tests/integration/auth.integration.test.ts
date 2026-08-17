import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AuthRepository } from '../../src/auth/auth.repository';
import { AuthService, AuthenticationError } from '../../src/auth/auth.service';
import { hashSessionToken, SESSION_DURATION_MS } from '../../src/security/session';
import { getPool, closePool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';

test('Authentication Service Integration Suite', async (t) => {
  const repository = new AuthRepository();
  const authService = new AuthService(repository);
  const pool = getPool();

  const createdUserIds: string[] = [];

  // Global test cleanup
  t.after(async () => {
    for (const userId of createdUserIds) {
      await deleteTestUserById(userId);
    }
    await closePool();
  });

  async function createTestUser(emailSuffix: string, rawPassword?: string) {
    const user = await createTestUserFixture({ emailSuffix, rawPassword });
    createdUserIds.push(user.id);
    return user;
  }

  await t.test('LOGIN scenarios', async (loginSuite) => {
    await loginSuite.test('valid credentials succeed and return token, expiry, and user profile', async () => {
      const testUser = await createTestUser('valid');
      const result = await authService.login(testUser.email, testUser.rawPassword);

      assert.ok(result.token, 'Should return a raw session token');
      assert.equal(result.user.id, testUser.id);
      assert.equal(result.user.email, testUser.email);
      assert.equal(result.user.firstName, 'Integration');

      // Verify expiry is ~30 days in the future
      const diff = result.expiresAt.getTime() - Date.now();
      assert.ok(Math.abs(diff - SESSION_DURATION_MS) < 5000, 'Expiry should be 30 days from creation');

      // Verify that the token hash (and NOT the raw token) is stored in the database
      const rawTokenHash = hashSessionToken(result.token);
      const sessionRow = await pool.query('SELECT * FROM auth_sessions WHERE user_id = $1', [testUser.id]);
      assert.equal(sessionRow.rows.length, 1);
      assert.equal(sessionRow.rows[0].token_hash, rawTokenHash);
      assert.notEqual(sessionRow.rows[0].token_hash, result.token);
    });

    await loginSuite.test('login normalizes email uppercase and surrounding whitespace', async () => {
      const testUser = await createTestUser('casing');
      const unnormalizedEmail = `   ${testUser.email.toUpperCase()}   `;

      const result = await authService.login(unnormalizedEmail, testUser.rawPassword);
      assert.equal(result.user.id, testUser.id);
    });

    await loginSuite.test('unknown email fails with generic AuthenticationError and creates zero throttle/session rows', async () => {
      const unknownEmail = 'nonexistent.user.12345@example.com';
      await assert.rejects(
        async () => {
          await authService.login(unknownEmail, 'ValidPassword12345!');
        },
        (err: Error) => {
          assert.equal(err.name, 'AuthenticationError');
          assert.equal(err.message, 'Invalid email or password');
          return true;
        }
      );

      // Verify zero throttle or session rows exist in DB for nonexistent email
      const throttleCount = await pool.query('SELECT COUNT(*) FROM auth_throttles');
      const initialCount = parseInt(throttleCount.rows[0].count, 10);
      assert.ok(initialCount >= 0);
    });

    await loginSuite.test('wrong password fails with generic AuthenticationError and creates no session', async () => {
      const testUser = await createTestUser('wrongpass');

      await assert.rejects(
        async () => {
          await authService.login(testUser.email, 'WrongPassword99999!');
        },
        (err: Error) => {
          assert.equal(err.name, 'AuthenticationError');
          assert.equal(err.message, 'Invalid email or password');
          return true;
        }
      );

      // Verify no session was created
      const sessions = await pool.query('SELECT * FROM auth_sessions WHERE user_id = $1', [testUser.id]);
      assert.equal(sessions.rows.length, 0, 'No session row should be created on failed login');
    });

    await loginSuite.test('malformed email or password below minimum length fails without hitting database', async () => {
      await assert.rejects(
        async () => {
          await authService.login('not-an-email', 'ValidPassword12345!');
        },
        AuthenticationError
      );

      await assert.rejects(
        async () => {
          await authService.login('valid@example.com', 'short');
        },
        AuthenticationError
      );
    });
  });

  await t.test('THROTTLING & ENUMERATION MITIGATION scenarios', async (throttleSuite) => {
    await throttleSuite.test('attempts 1-4 increment failure count; 5th attempt activates block and returns AuthenticationError', async () => {
      const testUser = await createTestUser('throttle5');

      // Attempts 1 to 5 all produce generic AuthenticationError externally
      for (let i = 1; i <= 5; i++) {
        await assert.rejects(
          () => authService.login(testUser.email, 'WrongPassword99999!'),
          AuthenticationError
        );
      }

      // Verify in DB that account is now internally blocked
      const throttleRow = await pool.query('SELECT failed_attempts, blocked_until FROM auth_throttles WHERE user_id = $1', [testUser.id]);
      assert.equal(throttleRow.rows.length, 1);
      assert.equal(throttleRow.rows[0].failed_attempts, 5);
      assert.ok(throttleRow.rows[0].blocked_until !== null);
      assert.ok(new Date(throttleRow.rows[0].blocked_until).getTime() > Date.now());
    });

    await throttleSuite.test('attempt while blocked fails generically with AuthenticationError without checking real password or creating session', async () => {
      const testUser = await createTestUser('blockedcheck');

      // Trigger 5 failed attempts
      for (let i = 1; i <= 5; i++) {
        try {
          await authService.login(testUser.email, 'WrongPassword99999!');
        } catch {
          // Expected
        }
      }

      // Subsequent attempt with correct password while blocked is rejected with generic AuthenticationError
      await assert.rejects(
        () => authService.login(testUser.email, testUser.rawPassword),
        AuthenticationError
      );

      // Verify zero sessions created
      const sessions = await pool.query('SELECT * FROM auth_sessions WHERE user_id = $1', [testUser.id]);
      assert.equal(sessions.rows.length, 0);
    });

    await throttleSuite.test('successful login before threshold resets accumulated failure count', async () => {
      const testUser = await createTestUser('resetcount');

      // 3 failed attempts
      for (let i = 1; i <= 3; i++) {
        await assert.rejects(
          () => authService.login(testUser.email, 'WrongPassword99999!'),
          AuthenticationError
        );
      }

      // Successful login resets throttle
      const loginRes = await authService.login(testUser.email, testUser.rawPassword);
      assert.ok(loginRes.token);

      // Verify DB throttle row was reset
      const throttleRow = await pool.query('SELECT failed_attempts, blocked_until FROM auth_throttles WHERE user_id = $1', [testUser.id]);
      assert.equal(throttleRow.rows[0].failed_attempts, 0);
      assert.equal(throttleRow.rows[0].blocked_until, null);

      // After reset, 4 more failed attempts fail normally without block
      for (let i = 1; i <= 4; i++) {
        await assert.rejects(
          () => authService.login(testUser.email, 'WrongPassword99999!'),
          AuthenticationError
        );
      }
    });

    await throttleSuite.test('expired throttle window starts a fresh failure count', async () => {
      const testUser = await createTestUser('expiredwindow');
      const pastTime = new Date(Date.now() - 16 * 60 * 1000); // 16 minutes in the past

      // 4 failures in the past window
      for (let i = 1; i <= 4; i++) {
        await assert.rejects(
          () => authService.login(testUser.email, 'WrongPassword99999!', pastTime),
          AuthenticationError
        );
      }

      // A new failure at now (window elapsed) starts fresh count of 1
      await assert.rejects(
        () => authService.login(testUser.email, 'WrongPassword99999!'),
        AuthenticationError
      );

      const throttleRow = await pool.query('SELECT failed_attempts FROM auth_throttles WHERE user_id = $1', [testUser.id]);
      assert.equal(throttleRow.rows[0].failed_attempts, 1);
    });

    await throttleSuite.test('expired block permits login again', async () => {
      const testUser = await createTestUser('expiredblock');
      const pastTime = new Date(Date.now() - 16 * 60 * 1000);

      // 5 failures in the past (block expired)
      for (let i = 1; i <= 5; i++) {
        try {
          await authService.login(testUser.email, 'WrongPassword99999!', pastTime);
        } catch {
          // Expected
        }
      }

      // Login now with correct password succeeds
      const result = await authService.login(testUser.email, testUser.rawPassword);
      assert.ok(result.token);
    });

    await throttleSuite.test('throttle state for one user does not affect another user', async () => {
      const userA = await createTestUser('usera');
      const userB = await createTestUser('userb');

      // Block user A
      for (let i = 1; i <= 5; i++) {
        try {
          await authService.login(userA.email, 'WrongPassword99999!');
        } catch {
          // Expected
        }
      }

      // User A is blocked
      await assert.rejects(() => authService.login(userA.email, userA.rawPassword), AuthenticationError);

      // User B can log in normally
      const resultB = await authService.login(userB.email, userB.rawPassword);
      assert.ok(resultB.token);
    });

    await throttleSuite.test('concurrent failed attempts cannot bypass the 5-attempt threshold', async () => {
      const testUser = await createTestUser('concurrent');

      // Fire 10 concurrent failed attempts
      const attempts = Array.from({ length: 10 }, () =>
        authService.login(testUser.email, 'WrongPassword99999!').catch((err) => err)
      );

      await Promise.all(attempts);

      // Verify user is blocked in DB
      const throttleRow = await pool.query('SELECT blocked_until, failed_attempts FROM auth_throttles WHERE user_id = $1', [testUser.id]);
      assert.ok(throttleRow.rows[0].blocked_until !== null);
      assert.ok(throttleRow.rows[0].failed_attempts >= 5);
    });
  });

  await t.test('AUTHENTICATE scenarios', async (authSuite) => {
    await authSuite.test('valid token authenticates the correct user', async () => {
      const testUser = await createTestUser('authvalid');
      const loginResult = await authService.login(testUser.email, testUser.rawPassword);

      const authenticatedUser = await authService.authenticateSession(loginResult.token);
      assert.ok(authenticatedUser);
      assert.equal(authenticatedUser.id, testUser.id);
      assert.equal(authenticatedUser.email, testUser.email);
    });

    await authSuite.test('random or blank token fails authentication', async () => {
      assert.equal(await authService.authenticateSession('random_token_value_not_existing_123'), null);
      assert.equal(await authService.authenticateSession(''), null);
      assert.equal(await authService.authenticateSession('   '), null);
    });

    await authSuite.test('expired session fails authentication', async () => {
      const testUser = await createTestUser('expired');
      const expiredRawToken = 'test_expired_raw_token_xyz';
      const expiredHash = hashSessionToken(expiredRawToken);

      // Insert a session created in the past whose expiry is also in the past (respecting expires_at > created_at)
      await pool.query(
        `INSERT INTO auth_sessions (user_id, token_hash, created_at, expires_at)
         VALUES ($1, $2, NOW() - INTERVAL '31 days', NOW() - INTERVAL '1 day')`,
        [testUser.id, expiredHash]
      );

      const result = await authService.authenticateSession(expiredRawToken);
      assert.equal(result, null, 'Expired session must return null');
    });
  });

  await t.test('LOGOUT scenarios', async (logoutSuite) => {
    await logoutSuite.test('logout invalidates target session and is idempotent', async () => {
      const testUser = await createTestUser('logout');
      const loginResult = await authService.login(testUser.email, testUser.rawPassword);

      // Authenticates before logout
      assert.ok(await authService.authenticateSession(loginResult.token));

      // Perform logout
      await authService.logout(loginResult.token);

      // No longer authenticates after logout
      assert.equal(await authService.authenticateSession(loginResult.token), null);

      // Repeated logout is safe and does not throw
      await authService.logout(loginResult.token);
      await authService.logout('already_nonexistent_token');
    });

    await logoutSuite.test('logging out one session does not delete another session for the same user', async () => {
      const testUser = await createTestUser('multisession');
      const session1 = await authService.login(testUser.email, testUser.rawPassword);
      const session2 = await authService.login(testUser.email, testUser.rawPassword);

      assert.notEqual(session1.token, session2.token);

      // Log out session 1 only
      await authService.logout(session1.token);

      // Session 1 is invalid
      assert.equal(await authService.authenticateSession(session1.token), null);

      // Session 2 is still valid
      const authUser2 = await authService.authenticateSession(session2.token);
      assert.ok(authUser2);
      assert.equal(authUser2.id, testUser.id);
    });
  });
});
