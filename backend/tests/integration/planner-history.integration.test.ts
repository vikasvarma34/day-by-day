import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool, closePool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';

test('GET /planner/history Integration Suite', async (t) => {
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
    userA = await createTestUserFixture({ emailSuffix: 'history.usera' });
    userB = await createTestUserFixture({ emailSuffix: 'history.userb' });
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

  async function getHistoryRequest(params: Record<string, string>, token?: string) {
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const query = new URLSearchParams(params).toString();
    const url = query ? `${baseUrl}/planner/history?${query}` : `${baseUrl}/planner/history`;
    const response = await fetch(url, { headers });
    const body = await response.json();
    return { status: response.status, body };
  }

  await t.test('Authentication: unauthenticated request is rejected with 401 UNAUTHORIZED', async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: '2026-08-18' });
    assert.equal(status, 401);
    assert.equal(body.error.code, 'UNAUTHORIZED');
  });

  await t.test('Date validation: missing plannerToday query parameter returns 400 BAD_REQUEST', async () => {
    const { status, body } = await getHistoryRequest({}, tokenA);
    assert.equal(status, 400);
    assert.equal(body.error.code, 'BAD_REQUEST');
    assert.match(body.error.message, /plannerToday/);
  });

  await t.test('Date validation: malformed, impossible, or year zero plannerToday returns 400 BAD_REQUEST', async () => {
    const badDates = ['2026-8-18', 'abc', '2026-02-30', '2025-02-29', '0000-01-01', '2026-13-01'];
    for (const badDate of badDates) {
      const { status, body } = await getHistoryRequest({ plannerToday: badDate }, tokenA);
      assert.equal(status, 400, `Expected 400 for ${badDate}`);
      assert.equal(body.error.code, 'BAD_REQUEST');
    }
  });

  await t.test('Date validation: valid leap date (2024-02-29) is accepted', async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: '2024-02-29' }, tokenA);
    assert.equal(status, 200);
    assert.deepEqual(body, { groups: [] });
  });

  await t.test('Empty state: user with no completions returns empty groups array', async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: '2026-08-18' }, tokenA);
    assert.equal(status, 200);
    assert.deepEqual(body, { groups: [] });
  });

  await t.test(
    'History classification, snapshot semantics, exclusions, search, and ordering',
    async () => {
      // 1. Qualifying Item A: Important ONCE task completed on 2026-08-17 (earlier completed_at: 10:00)
      const t1Res = await pool.query(
        `INSERT INTO tasks (user_id, title, note, is_important)
         VALUES ($1, 'Live Title T1', 'Note 1', false)
         RETURNING id`,
        [userA.id]
      );
      const t1Id = t1Res.rows[0].id;
      const s1Res = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-17', '2026-08-17')
         RETURNING id`,
        [t1Id]
      );
      const s1Id = s1Res.rows[0].id;
      const c1Res = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-17', '2026-08-17', '2026-08-17T10:00:00.000Z', 'Renew Passport Snapshot', true)
         RETURNING id`,
        [t1Id, s1Id]
      );
      const c1Id = c1Res.rows[0].id;

      // 2. Qualifying Item B: Important directly completed Later task on 2026-08-17 (later completed_at: 14:30 - should be first within 2026-08-17 group)
      const t2Res = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Live Title T2', false)
         RETURNING id`,
        [userA.id]
      );
      const t2Id = t2Res.rows[0].id;
      const c2Res = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, NULL, NULL, '2026-08-17', '2026-08-17T14:30:00.000Z', 'Submit Taxes Snapshot', true)
         RETURNING id`,
        [t2Id]
      );
      const c2Id = c2Res.rows[0].id;

      // 3. Qualifying Item C: Important ONCE task on an older date: 2026-08-15
      const t3Res = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Live Title T3', false)
         RETURNING id`,
        [userA.id]
      );
      const t3Id = t3Res.rows[0].id;
      const s3Res = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-15', '2026-08-15')
         RETURNING id`,
        [t3Id]
      );
      const s3Id = s3Res.rows[0].id;
      const c3Res = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-15', '2026-08-15', '2026-08-15T09:00:00.000Z', 'Fix Kitchen Sink Snapshot', true)
         RETURNING id`,
        [t3Id, s3Id]
      );
      const c3Id = c3Res.rows[0].id;

      // 4. Exclusion: Important recurring INTERVAL_DAYS completion on 2026-08-16 (must be excluded)
      const tRecurRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Interval Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tRecurId = tRecurRes.rows[0].id;
      const sRecurRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_days, interval_anchor_date)
         VALUES ($1, 'INTERVAL_DAYS', '2026-08-01', 1, '2026-08-01')
         RETURNING id`,
        [tRecurId]
      );
      const sRecurId = sRecurRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-16', '2026-08-16', '2026-08-16T08:00:00.000Z', 'Daily Standup Recurring Snapshot', true)`,
        [tRecurId, sRecurId]
      );

      // 5. Exclusion: Important recurring WEEKDAYS completion on 2026-08-16 (must be excluded)
      const tWeekRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Weekdays Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tWeekId = tWeekRes.rows[0].id;
      const sWeekRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, weekdays_mask)
         VALUES ($1, 'WEEKDAYS', '2026-08-01', 127)
         RETURNING id`,
        [tWeekId]
      );
      const sWeekId = sWeekRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-16', '2026-08-16', '2026-08-16T08:30:00.000Z', 'Weekdays Recurring Snapshot', true)`,
        [tWeekId, sWeekId]
      );

      // 6. Exclusion: Non-important ONCE completion (is_important_snapshot = false, even though tasks.is_important is currently true)
      const tNonImpOnceRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Non-imp Once Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tNonImpOnceId = tNonImpOnceRes.rows[0].id;
      const sNonImpOnceRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-16', '2026-08-16')
         RETURNING id`,
        [tNonImpOnceId]
      );
      const sNonImpOnceId = sNonImpOnceRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-16', '2026-08-16', '2026-08-16T09:00:00.000Z', 'Non Important Once Snapshot', false)`,
        [tNonImpOnceId, sNonImpOnceId]
      );

      // 7. Exclusion: Non-important direct Later completion (is_important_snapshot = false)
      const tNonImpLaterRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Non-imp Later Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tNonImpLaterId = tNonImpLaterRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, NULL, NULL, '2026-08-16', '2026-08-16T09:30:00.000Z', 'Non Important Later Snapshot', false)`,
        [tNonImpLaterId]
      );

      // 8. Exclusion: Completion on plannerToday itself (2026-08-18)
      const tTodayRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Today Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tTodayId = tTodayRes.rows[0].id;
      const sTodayRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-18', '2026-08-18')
         RETURNING id`,
        [tTodayId]
      );
      const sTodayId = sTodayRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-18', '2026-08-18', '2026-08-18T11:00:00.000Z', 'Today Completed Snapshot', true)`,
        [tTodayId, sTodayId]
      );

      // 9. User B Task: Important qualifying ONCE completion for User B
      const tUserBRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'User B Task', true)
         RETURNING id`,
        [userB.id]
      );
      const tUserBId = tUserBRes.rows[0].id;
      const sUserBRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-17', '2026-08-17')
         RETURNING id`,
        [tUserBId]
      );
      const sUserBId = sUserBRes.rows[0].id;
      const cUserBRes = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-17', '2026-08-17', '2026-08-17T12:00:00.000Z', 'User B Secret History Snapshot', true)
         RETURNING id`,
        [tUserBId, sUserBId]
      );
      const cUserBId = cUserBRes.rows[0].id;

      // --- Query User A History without search filter ---
      const { status: statusA, body: bodyA } = await getHistoryRequest(
        { plannerToday: '2026-08-18' },
        tokenA
      );
      assert.equal(statusA, 200);
      assert.equal(bodyA.groups.length, 2, 'Expected 2 date groups for User A');

      // Group 1: 2026-08-17 (items ordered newest completion first: c2 at 14:30 before c1 at 10:00)
      assert.equal(bodyA.groups[0].date, '2026-08-17');
      assert.equal(bodyA.groups[0].items.length, 2);
      assert.deepEqual(bodyA.groups[0].items[0], {
        taskId: t2Id,
        completionId: c2Id,
        title: 'Submit Taxes Snapshot',
        completedAt: '2026-08-17T14:30:00.000Z',
      });
      assert.deepEqual(bodyA.groups[0].items[1], {
        taskId: t1Id,
        completionId: c1Id,
        title: 'Renew Passport Snapshot',
        completedAt: '2026-08-17T10:00:00.000Z',
      });

      // Group 2: 2026-08-15
      assert.equal(bodyA.groups[1].date, '2026-08-15');
      assert.equal(bodyA.groups[1].items.length, 1);
      assert.deepEqual(bodyA.groups[1].items[0], {
        taskId: t3Id,
        completionId: c3Id,
        title: 'Fix Kitchen Sink Snapshot',
        completedAt: '2026-08-15T09:00:00.000Z',
      });

      // --- Query User A with case-insensitive search parameter: q=passport ---
      const { status: searchStatus, body: searchBody } = await getHistoryRequest(
        { plannerToday: '2026-08-18', q: 'PaSsPoRt' },
        tokenA
      );
      assert.equal(searchStatus, 200);
      assert.equal(searchBody.groups.length, 1);
      assert.equal(searchBody.groups[0].date, '2026-08-17');
      assert.equal(searchBody.groups[0].items.length, 1);
      assert.equal(searchBody.groups[0].items[0].title, 'Renew Passport Snapshot');

      // --- Query User A with whitespace-only q (should behave like no search) ---
      const { status: blankSearchStatus, body: blankSearchBody } = await getHistoryRequest(
        { plannerToday: '2026-08-18', q: '   ' },
        tokenA
      );
      assert.equal(blankSearchStatus, 200);
      assert.equal(blankSearchBody.groups.length, 2);

      // --- Query User A with non-matching search: q=nonexistent ---
      const { status: noMatchStatus, body: noMatchBody } = await getHistoryRequest(
        { plannerToday: '2026-08-18', q: 'nonexistent' },
        tokenA
      );
      assert.equal(noMatchStatus, 200);
      assert.deepEqual(noMatchBody, { groups: [] });

      // --- Query User B (Cross-user isolation) ---
      const { status: statusB, body: bodyB } = await getHistoryRequest(
        { plannerToday: '2026-08-18' },
        tokenB
      );
      assert.equal(statusB, 200);
      assert.equal(bodyB.groups.length, 1);
      assert.equal(bodyB.groups[0].date, '2026-08-17');
      assert.equal(bodyB.groups[0].items.length, 1);
      assert.deepEqual(bodyB.groups[0].items[0], {
        taskId: tUserBId,
        completionId: cUserBId,
        title: 'User B Secret History Snapshot',
        completedAt: '2026-08-17T12:00:00.000Z',
      });
    }
  );
});
