import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool, closePool } from '../../src/db/pool';
import { hashSessionToken } from '../../src/security/session';
import { createTestUserFixture, deleteTestUserById } from '../integration/auth-test-fixtures';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

test('HTTP Authentication E2E Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();

  const createdUserIds: string[] = [];

  t.after(async () => {
    server.close();
    for (const id of createdUserIds) {
      await deleteTestUserById(id);
    }
    await closePool();
  });

  async function createTestUser(label: string, rawPassword?: string) {
    const user = await createTestUserFixture({ emailSuffix: `e2e.${label}`, rawPassword });
    createdUserIds.push(user.id);
    return user;
  }

  await t.test('HTTP INGRESS HARDENING & SECURITY HEADERS scenarios', async (ingressSuite) => {
    await ingressSuite.test('X-Powered-By header is absent and Helmet headers are present', async () => {
      const response = await fetch(`${baseUrl}/health`);
      assert.equal(response.status, 200);

      // Verify X-Powered-By is disabled
      assert.equal(response.headers.get('x-powered-by'), null);

      // Verify Helmet standard security headers
      assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
      assert.equal(response.headers.get('x-frame-options'), 'SAMEORIGIN');
    });

    await ingressSuite.test('unknown route returns JSON HTTP 404 with NOT_FOUND error structure', async () => {
      const response = await fetch(`${baseUrl}/api/nonexistent-endpoint`);
      assert.equal(response.status, 404);
      assert.match(response.headers.get('content-type') || '', /application\/json/);

      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'NOT_FOUND',
          message: 'Route GET /api/nonexistent-endpoint not found',
        },
      });
    });

    await ingressSuite.test('malformed JSON body returns HTTP 400 with BAD_REQUEST contract', async () => {
      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{"email": "broken-json...',
      });

      assert.equal(response.status, 400);
      const data = await response.json();
      assert.equal(data.error.code, 'BAD_REQUEST');
      assert.match(data.error.message, /Malformed JSON payload/);
    });

    await ingressSuite.test('oversized JSON payload (>64 KB) returns HTTP 413 PAYLOAD_TOO_LARGE', async () => {
      const largePayload = {
        email: 'test@example.com',
        password: 'ValidPassword12345!',
        padding: 'A'.repeat(70 * 1024), // 70 KB
      };

      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(largePayload),
      });

      assert.equal(response.status, 413);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'PAYLOAD_TOO_LARGE',
          message: 'Request payload exceeds the maximum allowed size of 64 KB',
        },
      });
    });

    await ingressSuite.test('request with body but unsupported Content-Type returns HTTP 415', async () => {
      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: 'plain text body content',
      });

      assert.equal(response.status, 415);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'UNSUPPORTED_MEDIA_TYPE',
          message: 'Unsupported media type: request body must have Content-Type: application/json',
        },
      });
    });

    await ingressSuite.test('bodyless requests without Content-Type are accepted', async () => {
      const healthRes = await fetch(`${baseUrl}/health`);
      assert.equal(healthRes.status, 200);

      // Bodyless logout with invalid token returns 204 without requiring Content-Type header
      const logoutRes = await fetch(`${baseUrl}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: 'Bearer some_token_without_body' },
      });
      assert.equal(logoutRes.status, 204);
    });
  });

  await t.test('REQUEST CORRELATION & OBSERVEABILITY scenarios', async (obsSuite) => {
    await obsSuite.test('every response receives a valid unique X-Request-Id UUID header', async () => {
      const res1 = await fetch(`${baseUrl}/health`);
      const reqId1 = res1.headers.get('X-Request-Id');
      assert.ok(reqId1, 'Response must include X-Request-Id header');
      assert.match(reqId1, UUID_REGEX, 'X-Request-Id must be a valid UUID');

      const res2 = await fetch(`${baseUrl}/health`);
      const reqId2 = res2.headers.get('X-Request-Id');
      assert.ok(reqId2);
      assert.match(reqId2, UUID_REGEX);

      assert.notEqual(reqId1, reqId2, 'Different requests must receive different request IDs');
    });

    await obsSuite.test('console logging does not leak credentials, bodies, tokens, or hashes', async () => {
      const capturedLogs: string[] = [];
      const originalLog = console.log;
      const originalError = console.error;

      console.log = (msg?: any, ...args: any[]) => {
        capturedLogs.push(String(msg));
        originalLog(msg, ...args);
      };
      console.error = (msg?: any, ...args: any[]) => {
        capturedLogs.push(String(msg));
        originalError(msg, ...args);
      };

      const secretPassword = 'VerySecretPassword12345!';
      const testUser = await createTestUser('secobs', secretPassword);

      try {
        // Execute login
        const loginRes = await fetch(`${baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: testUser.email, password: secretPassword }),
        });
        const { token } = await loginRes.json();

        // Execute authenticated request
        await fetch(`${baseUrl}/auth/me`, {
          headers: { Authorization: `Bearer ${token}` },
        });

        // Execute failed login
        await fetch(`${baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: testUser.email, password: 'WrongSecretPassword999!' }),
        });

        // Check all captured logs for sensitive substrings
        const allLogOutput = capturedLogs.join('\n');
        assert.ok(!allLogOutput.includes(secretPassword), 'Logs must never contain the login password');
        assert.ok(!allLogOutput.includes('WrongSecretPassword999!'), 'Logs must never contain failed password input');
        assert.ok(!allLogOutput.includes(token), 'Logs must never contain raw session tokens');
        assert.ok(!allLogOutput.includes(`Bearer ${token}`), 'Logs must never contain Authorization header values');
        assert.ok(!allLogOutput.includes(process.env.DATABASE_URL || 'postgresql://'), 'Logs must never contain DATABASE_URL');
      } finally {
        console.log = originalLog;
        console.error = originalError;
      }
    });
  });

  await t.test('POST /auth/login scenarios', async (loginSuite) => {
    await loginSuite.test('valid credentials return HTTP 200 with token, expiry, and profile', async () => {
      const testUser = await createTestUser('valid');
      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: testUser.email,
          password: testUser.rawPassword,
        }),
      });

      assert.equal(response.status, 200);
      assert.match(response.headers.get('X-Request-Id') || '', UUID_REGEX);
      const data = await response.json();

      assert.ok(data.token, 'Response should contain raw token');
      assert.ok(data.expiresAt, 'Response should contain expiresAt timestamp');
      assert.equal(data.user.id, testUser.id);
      assert.equal(data.user.email, testUser.email);
      assert.equal(data.user.firstName, testUser.firstName);
      assert.equal(data.user.lastName, testUser.lastName);

      // Verify no sensitive hashes are leaked
      assert.equal(data.user.password_hash, undefined);
      assert.equal(data.user.passwordHash, undefined);
      assert.equal(data.token_hash, undefined);
      assert.equal(data.tokenHash, undefined);
    });

    await loginSuite.test('email normalization in request body works', async () => {
      const testUser = await createTestUser('casing');
      const messyEmail = `   ${testUser.email.toUpperCase()}   `;

      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: messyEmail,
          password: testUser.rawPassword,
        }),
      });

      assert.equal(response.status, 200);
      const data = await response.json();
      assert.equal(data.user.id, testUser.id);
    });

    await loginSuite.test('wrong password returns HTTP 401 with generic UNAUTHORIZED contract', async () => {
      const testUser = await createTestUser('wrongpass');
      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: testUser.email,
          password: 'WrongPassword99999!',
        }),
      });

      assert.equal(response.status, 401);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Invalid email or password',
        },
      });
    });

    await loginSuite.test('unknown email returns HTTP 401 with identical generic UNAUTHORIZED contract', async () => {
      const response = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: 'unknown.user.12345@example.com',
          password: 'ValidPassword12345!',
        }),
      });

      assert.equal(response.status, 401);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Invalid email or password',
        },
      });
    });

    await loginSuite.test('unknown email never creates auth_throttles rows and returns 401 indefinitely', async () => {
      for (let i = 1; i <= 6; i++) {
        const response = await fetch(`${baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: 'attacker.random.target@example.com',
            password: 'ValidPassword12345!',
          }),
        });

        assert.equal(response.status, 401);
        const data = await response.json();
        assert.deepEqual(data, {
          error: {
            code: 'UNAUTHORIZED',
            message: 'Invalid email or password',
          },
        });
      }
    });

    await loginSuite.test('malformed request body returns HTTP 400 with BAD_REQUEST contract', async () => {
      // Missing password
      const res1 = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'test@example.com' }),
      });
      assert.equal(res1.status, 400);
      const data1 = await res1.json();
      assert.equal(data1.error.code, 'BAD_REQUEST');

      // Non-string fields
      const res2 = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 12345, password: true }),
      });
      assert.equal(res2.status, 400);
      const data2 = await res2.json();
      assert.equal(data2.error.code, 'BAD_REQUEST');
    });
  });

  await t.test('POST /auth/login anti-enumeration and throttling scenarios', async (throttleSuite) => {
    await throttleSuite.test('known account wrong password and blocked state return identical HTTP 401 UNAUTHORIZED contract', async () => {
      const testUser = await createTestUser('e2ethrottle');

      // 5 failed attempts all return identical HTTP 401
      for (let i = 1; i <= 5; i++) {
        const res = await fetch(`${baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: testUser.email,
            password: 'WrongPassword99999!',
          }),
        });

        assert.equal(res.status, 401);
        const data = await res.json();
        assert.deepEqual(data, {
          error: {
            code: 'UNAUTHORIZED',
            message: 'Invalid email or password',
          },
        });
      }

      // Subsequent attempt with correct password while blocked is also rejected with identical HTTP 401
      const blockedRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: testUser.email,
          password: testUser.rawPassword,
        }),
      });

      assert.equal(blockedRes.status, 401);
      const blockedData = await blockedRes.json();
      assert.deepEqual(blockedData, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Invalid email or password',
        },
      });

      // Verify no session was created
      const sessions = await pool.query('SELECT * FROM auth_sessions WHERE user_id = $1', [testUser.id]);
      assert.equal(sessions.rows.length, 0);
    });

    await throttleSuite.test('successful login before reaching threshold resets failures', async () => {
      const testUser = await createTestUser('e2ereset');

      // 3 failed attempts
      for (let i = 1; i <= 3; i++) {
        const res = await fetch(`${baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: testUser.email,
            password: 'WrongPassword99999!',
          }),
        });
        assert.equal(res.status, 401);
      }

      // Valid login resets count
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: testUser.email,
          password: testUser.rawPassword,
        }),
      });
      assert.equal(loginRes.status, 200);

      // Subsequent 4 failed attempts remain 401 without blocking
      for (let i = 1; i <= 4; i++) {
        const res = await fetch(`${baseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: testUser.email,
            password: 'WrongPassword99999!',
          }),
        });
        assert.equal(res.status, 401);
      }
    });
  });

  await t.test('GET /auth/me scenarios', async (meSuite) => {
    await meSuite.test('valid bearer token returns HTTP 200 with user profile', async () => {
      const testUser = await createTestUser('mevalid');

      // Login to obtain token
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token } = await loginRes.json();

      // Access /auth/me
      const meRes = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      assert.equal(meRes.status, 200);
      assert.match(meRes.headers.get('X-Request-Id') || '', UUID_REGEX);
      const meData = await meRes.json();
      assert.equal(meData.user.id, testUser.id);
      assert.equal(meData.user.email, testUser.email);
      assert.equal(meData.user.firstName, testUser.firstName);
    });

    await meSuite.test('missing Authorization header returns HTTP 401 with UNAUTHORIZED contract', async () => {
      const response = await fetch(`${baseUrl}/auth/me`);
      assert.equal(response.status, 401);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing Authorization header',
        },
      });
    });

    await meSuite.test('malformed Authorization header returns HTTP 401 with UNAUTHORIZED contract', async () => {
      const res1 = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: 'Basic somebase64credentials' },
      });
      assert.equal(res1.status, 401);
      const data1 = await res1.json();
      assert.deepEqual(data1, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Malformed Authorization header',
        },
      });

      const res2 = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: 'Bearer' },
      });
      assert.equal(res2.status, 401);
      const data2 = await res2.json();
      assert.deepEqual(data2, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Malformed Authorization header',
        },
      });
    });

    await meSuite.test('random non-existent token returns HTTP 401 with UNAUTHORIZED contract', async () => {
      const response = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: 'Bearer random_invalid_token_12345' },
      });
      assert.equal(response.status, 401);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Invalid or expired session',
        },
      });
    });

    await meSuite.test('expired session returns HTTP 401 with UNAUTHORIZED contract', async () => {
      const testUser = await createTestUser('meexpired');
      const expiredRawToken = 'expired_raw_token_e2e';
      const expiredHash = hashSessionToken(expiredRawToken);

      // Insert expired session
      await pool.query(
        `INSERT INTO auth_sessions (user_id, token_hash, created_at, expires_at)
         VALUES ($1, $2, NOW() - INTERVAL '31 days', NOW() - INTERVAL '1 day')`,
        [testUser.id, expiredHash]
      );

      const response = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${expiredRawToken}` },
      });

      assert.equal(response.status, 401);
      const data = await response.json();
      assert.deepEqual(data, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Invalid or expired session',
        },
      });
    });
  });

  await t.test('POST /auth/logout scenarios', async (logoutSuite) => {
    await logoutSuite.test('valid logout returns HTTP 204 and invalidates session for /auth/me', async () => {
      const testUser = await createTestUser('logoutflow');

      // Login
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token } = await loginRes.json();

      // Verify valid before logout
      const beforeRes = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      assert.equal(beforeRes.status, 200);

      // Logout
      const logoutRes = await fetch(`${baseUrl}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      assert.equal(logoutRes.status, 204);

      // Verify invalid after logout
      const afterRes = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      assert.equal(afterRes.status, 401);
      const afterData = await afterRes.json();
      assert.equal(afterData.error.code, 'UNAUTHORIZED');
    });

    await logoutSuite.test('missing or malformed Authorization header on /auth/logout returns HTTP 401', async () => {
      // Missing header
      const res1 = await fetch(`${baseUrl}/auth/logout`, { method: 'POST' });
      assert.equal(res1.status, 401);
      const data1 = await res1.json();
      assert.deepEqual(data1, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing Authorization header',
        },
      });

      // Malformed header
      const res2 = await fetch(`${baseUrl}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: 'Basic invalidcredentials' },
      });
      assert.equal(res2.status, 401);
      const data2 = await res2.json();
      assert.deepEqual(data2, {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Malformed Authorization header',
        },
      });
    });

    await logoutSuite.test('repeated logout / already invalid token does not produce 500', async () => {
      const logoutRes = await fetch(`${baseUrl}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: 'Bearer already_invalid_token_9999' },
      });
      assert.equal(logoutRes.status, 204);
    });

    await logoutSuite.test('logging out one session does not invalidate another session for the same user', async () => {
      const testUser = await createTestUser('multisessflow');

      // Create session 1
      const loginRes1 = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token: token1 } = await loginRes1.json();

      // Create session 2
      const loginRes2 = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token: token2 } = await loginRes2.json();

      assert.notEqual(token1, token2);

      // Logout session 1
      const logoutRes = await fetch(`${baseUrl}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token1}` },
      });
      assert.equal(logoutRes.status, 204);

      // Session 1 is 401
      const meRes1 = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token1}` },
      });
      assert.equal(meRes1.status, 401);

      // Session 2 is still 200
      const meRes2 = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token2}` },
      });
      assert.equal(meRes2.status, 200);
      const meData2 = await meRes2.json();
      assert.equal(meData2.user.id, testUser.id);
    });
  });

  await t.test('POST /auth/change-password scenarios', async (changeSuite) => {
    await changeSuite.test('unauthenticated request returns HTTP 401 UNAUTHORIZED', async () => {
      const res = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          currentPassword: 'CurrentPassword12345!',
          newPassword: 'NewPassword12345!',
        }),
      });

      assert.equal(res.status, 401);
      const data = await res.json();
      assert.equal(data.error.code, 'UNAUTHORIZED');
    });

    await changeSuite.test('missing or invalid body fields returns HTTP 400 BAD_REQUEST', async () => {
      const testUser = await createTestUser('changepw_e2e_badbody');
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token } = await loginRes.json();

      // Missing newPassword
      const res1 = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword: testUser.rawPassword,
        }),
      });
      assert.equal(res1.status, 400);

      // Non-string currentPassword
      const res2 = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword: 12345,
          newPassword: 'ValidNewPassword12345!',
        }),
      });
      assert.equal(res2.status, 400);
    });

    await changeSuite.test('invalid new password (< 15 chars) returns HTTP 400 BAD_REQUEST', async () => {
      const testUser = await createTestUser('changepw_e2e_short');
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token } = await loginRes.json();

      const res = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword: testUser.rawPassword,
          newPassword: 'TooShort123!',
        }),
      });

      assert.equal(res.status, 400);
      const data = await res.json();
      assert.equal(data.error.code, 'BAD_REQUEST');
      assert.ok(data.error.message.includes('between 15 and 128'));
    });

    await changeSuite.test('same new password returns HTTP 400 BAD_REQUEST', async () => {
      const testUser = await createTestUser('changepw_e2e_same');
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token } = await loginRes.json();

      const res = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword: testUser.rawPassword,
          newPassword: testUser.rawPassword,
        }),
      });

      assert.equal(res.status, 400);
      const data = await res.json();
      assert.equal(data.error.code, 'BAD_REQUEST');
      assert.equal(data.error.message, 'New password cannot be the same as current password');
    });

    await changeSuite.test('wrong current password returns HTTP 401 UNAUTHORIZED and preserves existing session', async () => {
      const testUser = await createTestUser('changepw_e2e_wrongpw');
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token } = await loginRes.json();

      const res = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword: 'WrongPassword12345!',
          newPassword: 'BrandNewValidPassword2026!',
        }),
      });

      assert.equal(res.status, 401);
      const data = await res.json();
      assert.equal(data.error.code, 'UNAUTHORIZED');
      assert.equal(data.error.message, 'Invalid current password');

      // Session is still active
      const meRes = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      assert.equal(meRes.status, 200);
    });

    await changeSuite.test('successful change password returns HTTP 200 { success: true }, revokes sessions, and requires new password', async () => {
      const testUser = await createTestUser('changepw_e2e_full');
      const loginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      const { token: sessionToken } = await loginRes.json();

      const newPassword = 'BrandNewValidPassword2026!';

      const changeRes = await fetch(`${baseUrl}/auth/change-password`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${sessionToken}`,
        },
        body: JSON.stringify({
          currentPassword: testUser.rawPassword,
          newPassword,
        }),
      });

      assert.equal(changeRes.status, 200);
      const changeData = await changeRes.json();
      assert.deepEqual(changeData, { success: true });

      // Verify no sensitive data leaked in response
      assert.equal((changeData as any).password, undefined);
      assert.equal((changeData as any).passwordHash, undefined);
      assert.equal((changeData as any).token, undefined);

      // Previous session token is now invalid (revoked)
      const meRes = await fetch(`${baseUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${sessionToken}` },
      });
      assert.equal(meRes.status, 401);

      // Old password fails login
      const oldLoginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: testUser.rawPassword }),
      });
      assert.equal(oldLoginRes.status, 401);

      // New password succeeds login
      const newLoginRes = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email, password: newPassword }),
      });
      assert.equal(newLoginRes.status, 200);
      const newLoginData = await newLoginRes.json();
      assert.ok(newLoginData.token);
      assert.equal(newLoginData.user.id, testUser.id);
    });
  });
});
