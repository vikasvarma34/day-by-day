import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool, closePool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';

test('GET /planner/later Integration Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();

  let userA: any;
  let userB: any;
  let tokenA: string;
  let tokenB: string;

  const createdUserIds: string[] = [];

  t.before(async () => {
    userA = await createTestUserFixture({ emailSuffix: 'later.usera' });
    userB = await createTestUserFixture({ emailSuffix: 'later.userb' });
    createdUserIds.push(userA.id, userB.id);

    // Active session for userA
    tokenA = generateSessionToken();
    const tokenHashA = hashSessionToken(tokenA);
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
      [userA.id, tokenHashA, expiresAt]
    );

    // Active session for userB
    tokenB = generateSessionToken();
    const tokenHashB = hashSessionToken(tokenB);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
      [userB.id, tokenHashB, expiresAt]
    );
  });

  t.after(async () => {
    server.close();
    for (const id of createdUserIds) {
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [id]);
      await deleteTestUserById(id);
    }
    await closePool();
  });

  async function getLaterRequest(token?: string) {
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const response = await fetch(`${baseUrl}/planner/later`, { headers });
    const body = await response.json();
    return { status: response.status, body };
  }

  await t.test('Authentication: unauthenticated request is rejected with 401 UNAUTHORIZED', async () => {
    const { status, body } = await getLaterRequest();
    assert.equal(status, 401);
    assert.equal(body.error.code, 'UNAUTHORIZED');
  });

  await t.test('Empty state: authenticated user with no Later tasks receives empty items array', async () => {
    const { status, body } = await getLaterRequest(tokenA);
    assert.equal(status, 200);
    assert.deepEqual(body, { items: [] });
  });

  await t.test(
    'Later classification, deterministic ordering, and exclusions',
    async () => {
      // 1. Create true Later tasks for User A with staggered created_at times
      // Task 1: Non-important, note present, created earlier
      const t1Res = await pool.query(
        `INSERT INTO tasks (user_id, title, note, is_important, created_at)
         VALUES ($1, 'Buy groceries', 'Apples and oats', false, '2026-08-01T10:00:00.000Z')
         RETURNING id`,
        [userA.id]
      );
      const t1Id = t1Res.rows[0].id;

      // Task 2: Important, note null, created slightly later (must NOT jump to top)
      const t2Res = await pool.query(
        `INSERT INTO tasks (user_id, title, note, is_important, created_at)
         VALUES ($1, 'Renew passport', NULL, true, '2026-08-01T11:00:00.000Z')
         RETURNING id`,
        [userA.id]
      );
      const t2Id = t2Res.rows[0].id;

      // Task 3: Non-important, note present, created latest
      const t3Res = await pool.query(
        `INSERT INTO tasks (user_id, title, note, is_important, created_at)
         VALUES ($1, 'Read book chapter', 'Chapter 4', false, '2026-08-01T12:00:00.000Z')
         RETURNING id`,
        [userA.id]
      );
      const t3Id = t3Res.rows[0].id;

      // 2. Exclusion Case A: Task with ONCE schedule
      const tOnceRes = await pool.query(
        `INSERT INTO tasks (user_id, title) VALUES ($1, 'ONCE Scheduled Task') RETURNING id`,
        [userA.id]
      );
      const tOnceId = tOnceRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-18', '2026-08-18')`,
        [tOnceId]
      );

      // 3. Exclusion Case B: Task with active recurring schedule (INTERVAL_DAYS)
      const tRecurRes = await pool.query(
        `INSERT INTO tasks (user_id, title) VALUES ($1, 'Recurring Active Task') RETURNING id`,
        [userA.id]
      );
      const tRecurId = tRecurRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_days, interval_anchor_date)
         VALUES ($1, 'INTERVAL_DAYS', '2026-08-01', 2, '2026-08-01')`,
        [tRecurId]
      );

      // 4. Exclusion Case C: Task with a finite schedule that already ended in the past
      const tExpiredRes = await pool.query(
        `INSERT INTO tasks (user_id, title) VALUES ($1, 'Expired Finite Schedule Task') RETURNING id`,
        [userA.id]
      );
      const tExpiredId = tExpiredRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date)
         VALUES ($1, 'INTERVAL_DAYS', '2026-07-01', '2026-07-15', 1, '2026-07-01')`,
        [tExpiredId]
      );

      // 5. Exclusion Case D: Directly completed Later task (no schedule, but has completion row)
      const tCompletedRes = await pool.query(
        `INSERT INTO tasks (user_id, title) VALUES ($1, 'Directly Completed Later Task') RETURNING id`,
        [userA.id]
      );
      const tCompletedId = tCompletedRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
         VALUES ($1, NULL, NULL, '2026-08-18', '2026-08-18T10:00:00.000Z', false)`,
        [tCompletedId]
      );

      // 6. User B Task: True Later task belonging to another user
      const tUserBRes = await pool.query(
        `INSERT INTO tasks (user_id, title, note, is_important)
         VALUES ($1, 'User B Secret Later Task', 'Secret note', false)
         RETURNING id`,
        [userB.id]
      );
      const tUserBId = tUserBRes.rows[0].id;

      // Query GET /planner/later as User A
      const { status: statusA, body: bodyA } = await getLaterRequest(tokenA);
      assert.equal(statusA, 200);
      assert.ok(Array.isArray(bodyA.items));
      assert.equal(bodyA.items.length, 3, 'User A should receive exactly 3 Later tasks');

      // Verify exact contract and deterministic created_at ASC order (t1 -> t2 -> t3)
      assert.deepEqual(bodyA.items, [
        {
          taskId: t1Id,
          title: 'Buy groceries',
          note: 'Apples and oats',
          isImportant: false,
        },
        {
          taskId: t2Id,
          title: 'Renew passport',
          note: null,
          isImportant: true,
        },
        {
          taskId: t3Id,
          title: 'Read book chapter',
          note: 'Chapter 4',
          isImportant: false,
        },
      ]);

      // Query GET /planner/later as User B (Cross-user isolation)
      const { status: statusB, body: bodyB } = await getLaterRequest(tokenB);
      assert.equal(statusB, 200);
      assert.equal(bodyB.items.length, 1);
      assert.deepEqual(bodyB.items, [
        {
          taskId: tUserBId,
          title: 'User B Secret Later Task',
          note: 'Secret note',
          isImportant: false,
        },
      ]);
    }
  );
});
