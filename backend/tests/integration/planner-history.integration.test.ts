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
    const { status, body } = await getHistoryRequest({ plannerToday: '2026-08-20' });
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

  await t.test('Date validation: valid UTC-1 date (2026-08-19) is accepted', async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: '2026-08-19' }, tokenA);
    assert.equal(status, 200);
    assert.deepEqual(body, { groups: [] });
  });

  await t.test('Empty state: user with no completions returns empty groups array', async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: '2026-08-20' }, tokenA);
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

      // 8. Exclusion: Completion on plannerToday itself (2026-08-20)
      const tTodayRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Today Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tTodayId = tTodayRes.rows[0].id;
      const sTodayRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', '2026-08-20', '2026-08-20')
         RETURNING id`,
        [tTodayId]
      );
      const sTodayId = sTodayRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, '2026-08-20', '2026-08-20', '2026-08-20T11:00:00.000Z', 'Today Completed Snapshot', true)`,
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
        { plannerToday: '2026-08-20' },
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
        { plannerToday: '2026-08-20', q: 'PaSsPoRt' },
        tokenA
      );
      assert.equal(searchStatus, 200);
      assert.equal(searchBody.groups.length, 1);
      assert.equal(searchBody.groups[0].date, '2026-08-17');
      assert.equal(searchBody.groups[0].items.length, 1);
      assert.equal(searchBody.groups[0].items[0].title, 'Renew Passport Snapshot');

      // --- Query User A with whitespace-only q (should behave like no search) ---
      const { status: blankSearchStatus, body: blankSearchBody } = await getHistoryRequest(
        { plannerToday: '2026-08-20', q: '   ' },
        tokenA
      );
      assert.equal(blankSearchStatus, 200);
      assert.equal(blankSearchBody.groups.length, 2);

      // --- Query User A with non-matching search: q=nonexistent ---
      const { status: noMatchStatus, body: noMatchBody } = await getHistoryRequest(
        { plannerToday: '2026-08-20', q: 'nonexistent' },
        tokenA
      );
      assert.equal(noMatchStatus, 200);
      assert.deepEqual(noMatchBody, { groups: [] });

      // --- Query User B (Cross-user isolation) ---
      const { status: statusB, body: bodyB } = await getHistoryRequest(
        { plannerToday: '2026-08-20' },
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

  await t.test('Post-completion isImportant edit synchronization integration tests', async (sub) => {
    const getValidId = () => crypto.randomUUID();

    await sub.test('1-3: non-important ONCE completion -> mark Important -> in History with original date/timestamp -> unmark -> removed', async () => {
      const taskId = getValidId();
      const schedId = getValidId();

      // Create non-important ONCE task
      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'ONCE Task 1', NULL, false)`,
        [taskId, userA.id]
      );
      await pool.query(
        `INSERT INTO task_schedules (id, task_id, schedule_type, start_date, end_date) VALUES ($1, $2, 'ONCE', '2026-08-15', '2026-08-15')`,
        [schedId, taskId]
      );

      // Complete it on 2026-08-15
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-15', scheduleId: schedId, scheduledDate: '2026-08-15' })
      });
      assert.equal(compRes.status, 200);

      // Verify title_snapshot was stored even though is_important was false
      const compDb1 = await pool.query('SELECT title_snapshot, is_important_snapshot, completed_date::text, completed_at FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb1.rows.length, 1);
      assert.equal(compDb1.rows[0].title_snapshot, 'ONCE Task 1');
      assert.equal(compDb1.rows[0].is_important_snapshot, false);
      const originalCompletedDate = compDb1.rows[0].completed_date;
      const originalCompletedAt = compDb1.rows[0].completed_at;

      // 1. Initially not in History
      const h1 = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'ONCE Task 1' }, tokenA);
      assert.deepEqual(h1.body.groups, []);

      // 2. Mark important via PATCH /tasks/:taskId
      const patchRes1 = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: true })
      });
      assert.equal(patchRes1.status, 200);

      // Verify completion row in DB: is_important_snapshot became true, completed_date & completed_at UNCHANGED
      const compDb2 = await pool.query('SELECT title_snapshot, is_important_snapshot, completed_date::text, completed_at FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb2.rows[0].is_important_snapshot, true);
      assert.equal(compDb2.rows[0].title_snapshot, 'ONCE Task 1');
      assert.equal(compDb2.rows[0].completed_date, originalCompletedDate);
      assert.equal(compDb2.rows[0].completed_at.toISOString(), originalCompletedAt.toISOString());

      // Query History: now appears under original completed_date (2026-08-15)
      const h2 = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'ONCE Task 1' }, tokenA);
      assert.equal(h2.body.groups.length, 1);
      assert.equal(h2.body.groups[0].date, '2026-08-15');
      assert.equal(h2.body.groups[0].items.length, 1);
      assert.equal(h2.body.groups[0].items[0].taskId, taskId);
      assert.equal(h2.body.groups[0].items[0].title, 'ONCE Task 1');

      // 3. Mark non-important again via PATCH
      const patchRes2 = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: false })
      });
      assert.equal(patchRes2.status, 200);

      // Query History: disappeared
      const h3 = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'ONCE Task 1' }, tokenA);
      assert.deepEqual(h3.body.groups, []);
    });

    await sub.test('4-5: non-important direct-Later completion -> mark Important -> in History -> unmark -> removed', async () => {
      const taskId = getValidId();

      // Create non-important Later task
      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'Later Task Alpha', NULL, false)`,
        [taskId, userA.id]
      );

      // Complete direct Later task
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      assert.equal(compRes.status, 200);

      // 4. Mark important
      const patchRes1 = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: true })
      });
      assert.equal(patchRes1.status, 200);

      // Query History: appears
      const h1 = await getHistoryRequest({ plannerToday: '2026-08-21', q: 'Later Task Alpha' }, tokenA);
      assert.equal(h1.body.groups.length, 1);
      assert.equal(h1.body.groups[0].date, '2026-08-20');
      assert.equal(h1.body.groups[0].items[0].taskId, taskId);

      // 5. Unmark important
      const patchRes2 = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: false })
      });
      assert.equal(patchRes2.status, 200);

      const h2 = await getHistoryRequest({ plannerToday: '2026-08-21', q: 'Later Task Alpha' }, tokenA);
      assert.deepEqual(h2.body.groups, []);
    });

    await sub.test('6: recurring completed occurrence -> toggle live Important -> still excluded from History', async () => {
      const taskId = getValidId();
      const schedId = getValidId();

      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'Daily Standup Recur', NULL, false)`,
        [taskId, userA.id]
      );
      await pool.query(
        `INSERT INTO task_schedules (id, task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date) VALUES ($1, $2, 'INTERVAL_DAYS', '2026-08-10', '2026-08-30', 1, '2026-08-10')`,
        [schedId, taskId]
      );

      // Complete on 2026-08-14
      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-14', scheduleId: schedId, scheduledDate: '2026-08-14' })
      });

      // Verify recurring completion has title_snapshot = null
      const compDb = await pool.query('SELECT title_snapshot, is_important_snapshot FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb.rows[0].title_snapshot, null);

      // Mark live task important
      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: true })
      });

      // Query History: recurring completion must NOT appear
      const h = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'Daily Standup Recur' }, tokenA);
      assert.deepEqual(h.body.groups, []);
    });

    await sub.test('7: incomplete task importance edit -> no completion mutation', async () => {
      const taskId = getValidId();
      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'Incomplete Task', NULL, false)`,
        [taskId, userA.id]
      );

      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: true })
      });

      const compDb = await pool.query('SELECT * FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb.rows.length, 0);
    });

    await sub.test('10: title edited AFTER completion -> existing title_snapshot remains unchanged', async () => {
      const taskId = getValidId();
      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'Original Title at Completion', NULL, true)`,
        [taskId, userA.id]
      );

      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ plannerToday: '2026-08-19', completedDate: '2026-08-19' })
      });

      // Later edit title to "Edited Live Title"
      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'Edited Live Title' })
      });

      const h = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'Original Title' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].items[0].title, 'Original Title at Completion');
    });

    await sub.test('11: legacy completion with title_snapshot NULL + isImportant false -> true -> fallback title populated', async () => {
      const taskId = getValidId();
      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'Legacy Task Pre-Update', NULL, false)`,
        [taskId, userA.id]
      );
      // Legacy completion inserted with NULL title_snapshot
      await pool.query(
        `INSERT INTO task_completions (task_id, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, '2026-08-15', NOW(), NULL, false)`,
        [taskId]
      );

      // PATCH isImportant = true
      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: true })
      });

      const compDb = await pool.query('SELECT title_snapshot, is_important_snapshot FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb.rows[0].is_important_snapshot, true);
      assert.equal(compDb.rows[0].title_snapshot, 'Legacy Task Pre-Update');

      const h = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'Legacy Task' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].items[0].title, 'Legacy Task Pre-Update');
    });

    await sub.test('12: same PATCH changes title + isImportant on legacy NULL snapshot -> fallback uses PRE-PATCH locked title', async () => {
      const taskId = getValidId();
      await pool.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important) VALUES ($1, $2, 'Historical Locked Title', NULL, false)`,
        [taskId, userA.id]
      );
      await pool.query(
        `INSERT INTO task_completions (task_id, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, '2026-08-15', NOW(), NULL, false)`,
        [taskId]
      );

      // Single PATCH with BOTH title: 'Brand New Title' and isImportant: true
      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'Brand New Title', isImportant: true })
      });

      const compDb = await pool.query('SELECT title_snapshot, is_important_snapshot FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb.rows[0].is_important_snapshot, true);
      assert.equal(compDb.rows[0].title_snapshot, 'Historical Locked Title');

      const h = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'Historical Locked' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].items[0].title, 'Historical Locked Title');
    });

    await sub.test('13: historical ONCE task creation, completion backfill, and history visibility lifecycle', async () => {
      const taskId = getValidId();
      // 1. Create historical ONCE task on 2026-08-16 while plannerToday is 2026-08-20
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Historical Backfilled ONCE Task',
          isImportant: true,
          plannerToday: '2026-08-20',
          schedule: { type: 'ONCE', startDate: '2026-08-16' }
        })
      });
      assert.equal(createRes.status, 201);
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      // 2. Complete with historical completedDate = 2026-08-16
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-16',
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(compRes.status, 200);

      // 3. Immediately appears in History on 2026-08-20 grouped under 2026-08-16
      const h1 = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'Backfilled' }, tokenA);
      assert.equal(h1.body.groups.length, 1);
      assert.equal(h1.body.groups[0].date, '2026-08-16');
      assert.equal(h1.body.groups[0].items.length, 1);
      assert.equal(h1.body.groups[0].items[0].title, 'Historical Backfilled ONCE Task');

      // 4. Undo completion removes it from History
      const undoRes = await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(undoRes.status, 200);

      const h2 = await getHistoryRequest({ plannerToday: '2026-08-20', q: 'Backfilled' }, tokenA);
      assert.equal(h2.body.groups.length, 0);
    });
  });
});
