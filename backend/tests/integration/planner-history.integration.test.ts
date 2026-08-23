import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool, closePool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById, getTestDate } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';
import crypto from 'node:crypto';

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

  const today = getTestDate(0);
  const yesterday = getTestDate(-1);
  const tomorrow = getTestDate(1);
  const dayAfterTomorrow = getTestDate(2);
  const dMinus2 = getTestDate(-2);
  const dMinus3 = getTestDate(-3);
  const dMinus4 = getTestDate(-4);
  const dMinus5 = getTestDate(-5);
  const dMinus6 = getTestDate(-6);

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
    const { status, body } = await getHistoryRequest({ plannerToday: today });
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

  await t.test(`Date validation: valid UTC-1 date (${yesterday}) is accepted`, async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: yesterday }, tokenA);
    assert.equal(status, 200);
    assert.deepEqual(body, { groups: [] });
  });

  await t.test('Empty state: user with no completions returns empty groups array', async () => {
    const { status, body } = await getHistoryRequest({ plannerToday: today }, tokenA);
    assert.equal(status, 200);
    assert.deepEqual(body, { groups: [] });
  });

  await t.test(
    'History classification, snapshot semantics, exclusions, search, and ordering',
    async () => {
      // 1. Qualifying Item A: Important ONCE task completed on dMinus3 (earlier completed_at: 10:00)
      const t1Res = await pool.query(
        `INSERT INTO tasks (user_id, title, note, is_important)
         VALUES ($1, 'Live Title T1', 'Note 1', false)
         RETURNING id`,
        [userA.id]
      );
      const t1Id = t1Res.rows[0].id;
      const s1Res = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', $2, $2)
         RETURNING id`,
        [t1Id, dMinus3]
      );
      const s1Id = s1Res.rows[0].id;
      const c1Res = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'Renew Passport Snapshot', true)
         RETURNING id`,
        [t1Id, s1Id, dMinus3, `${dMinus3}T10:00:00.000Z`]
      );
      const c1Id = c1Res.rows[0].id;

      // 2. Qualifying Item B: Important directly completed Later task on dMinus3 (later completed_at: 14:30 - should be first within dMinus3 group)
      const t2Res = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Live Title T2', false)
         RETURNING id`,
        [userA.id]
      );
      const t2Id = t2Res.rows[0].id;
      const c2Res = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, NULL, NULL, $2, $3, 'Submit Taxes Snapshot', true)
         RETURNING id`,
        [t2Id, dMinus3, `${dMinus3}T14:30:00.000Z`]
      );
      const c2Id = c2Res.rows[0].id;

      // 3. Qualifying Item C: Important ONCE task on an older date: dMinus5
      const t3Res = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Live Title T3', false)
         RETURNING id`,
        [userA.id]
      );
      const t3Id = t3Res.rows[0].id;
      const s3Res = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', $2, $2)
         RETURNING id`,
        [t3Id, dMinus5]
      );
      const s3Id = s3Res.rows[0].id;
      const c3Res = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'Fix Kitchen Sink Snapshot', true)
         RETURNING id`,
        [t3Id, s3Id, dMinus5, `${dMinus5}T09:00:00.000Z`]
      );
      const c3Id = c3Res.rows[0].id;

      // 4. Exclusion: Important recurring INTERVAL_DAYS completion on dMinus4 (must be excluded)
      const tRecurRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Interval Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tRecurId = tRecurRes.rows[0].id;
      const sRecurRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_days, interval_anchor_date)
         VALUES ($1, 'INTERVAL_DAYS', $2, 1, $2)
         RETURNING id`,
        [tRecurId, dMinus6]
      );
      const sRecurId = sRecurRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'Daily Standup Recurring Snapshot', true)`,
        [tRecurId, sRecurId, dMinus4, `${dMinus4}T08:00:00.000Z`]
      );

      // 5. Exclusion: Important recurring WEEKDAYS completion on dMinus4 (must be excluded)
      const tWeekRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Weekdays Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tWeekId = tWeekRes.rows[0].id;
      const sWeekRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, weekdays_mask)
         VALUES ($1, 'WEEKDAYS', $2, 127)
         RETURNING id`,
        [tWeekId, dMinus6]
      );
      const sWeekId = sWeekRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'Weekdays Recurring Snapshot', true)`,
        [tWeekId, sWeekId, dMinus4, `${dMinus4}T08:30:00.000Z`]
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
         VALUES ($1, 'ONCE', $2, $2)
         RETURNING id`,
        [tNonImpOnceId, dMinus4]
      );
      const sNonImpOnceId = sNonImpOnceRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'Non Important Once Snapshot', false)`,
        [tNonImpOnceId, sNonImpOnceId, dMinus4, `${dMinus4}T09:00:00.000Z`]
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
         VALUES ($1, NULL, NULL, $2, $3, 'Non Important Later Snapshot', false)`,
        [tNonImpLaterId, dMinus4, `${dMinus4}T09:30:00.000Z`]
      );

      // 8. Exclusion: Completion on a date strictly after plannerToday (tomorrow when plannerToday is today)
      const tFutureCompRes = await pool.query(
        `INSERT INTO tasks (user_id, title, is_important)
         VALUES ($1, 'Future Comp Task', true)
         RETURNING id`,
        [userA.id]
      );
      const tFutureCompId = tFutureCompRes.rows[0].id;
      const sFutureCompRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
         VALUES ($1, 'ONCE', $2, $2)
         RETURNING id`,
        [tFutureCompId, tomorrow]
      );
      const sFutureCompId = sFutureCompRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'Future Completed Snapshot', true)`,
        [tFutureCompId, sFutureCompId, tomorrow, `${tomorrow}T11:00:00.000Z`]
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
         VALUES ($1, 'ONCE', $2, $2)
         RETURNING id`,
        [tUserBId, dMinus3]
      );
      const sUserBId = sUserBRes.rows[0].id;
      const cUserBRes = await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot)
         VALUES ($1, $2, $3, $3, $4, 'User B Secret History Snapshot', true)
         RETURNING id`,
        [tUserBId, sUserBId, dMinus3, `${dMinus3}T12:00:00.000Z`]
      );
      const cUserBId = cUserBRes.rows[0].id;

      // --- Query User A History without search filter ---
      const { status: statusA, body: bodyA } = await getHistoryRequest(
        { plannerToday: today },
        tokenA
      );
      assert.equal(statusA, 200);
      assert.ok(Array.isArray(bodyA.groups));
      assert.equal(bodyA.groups.length, 2, 'Should have exactly 2 date groups for User A');

      // Group 1: dMinus3 (ordered desc by date)
      assert.equal(bodyA.groups[0].date, dMinus3);
      assert.equal(bodyA.groups[0].items.length, 2);
      // Within group: ordered desc by completed_at (Submit Taxes at 14:30 before Renew Passport at 10:00)
      assert.deepEqual(bodyA.groups[0].items[0], {
        taskId: t2Id,
        completionId: c2Id,
        title: 'Submit Taxes Snapshot',
        completedAt: `${dMinus3}T14:30:00.000Z`,
      });
      assert.deepEqual(bodyA.groups[0].items[1], {
        taskId: t1Id,
        completionId: c1Id,
        title: 'Renew Passport Snapshot',
        completedAt: `${dMinus3}T10:00:00.000Z`,
      });

      // Group 2: dMinus5
      assert.equal(bodyA.groups[1].date, dMinus5);
      assert.equal(bodyA.groups[1].items.length, 1);
      assert.deepEqual(bodyA.groups[1].items[0], {
        taskId: t3Id,
        completionId: c3Id,
        title: 'Fix Kitchen Sink Snapshot',
        completedAt: `${dMinus5}T09:00:00.000Z`,
      });

      // --- Query User A with case-insensitive search parameter: q=passport ---
      const { status: searchStatus, body: searchBody } = await getHistoryRequest(
        { plannerToday: today, q: 'PaSsPoRt' },
        tokenA
      );
      assert.equal(searchStatus, 200);
      assert.equal(searchBody.groups.length, 1);
      assert.equal(searchBody.groups[0].date, dMinus3);
      assert.equal(searchBody.groups[0].items.length, 1);
      assert.equal(searchBody.groups[0].items[0].title, 'Renew Passport Snapshot');

      // --- Query User A with whitespace-only q (should behave like no search) ---
      const { status: blankSearchStatus, body: blankSearchBody } = await getHistoryRequest(
        { plannerToday: today, q: '   ' },
        tokenA
      );
      assert.equal(blankSearchStatus, 200);
      assert.equal(blankSearchBody.groups.length, 2);

      // --- Query User A with non-matching search: q=nonexistent ---
      const { status: noMatchStatus, body: noMatchBody } = await getHistoryRequest(
        { plannerToday: today, q: 'nonexistent' },
        tokenA
      );
      assert.equal(noMatchStatus, 200);
      assert.deepEqual(noMatchBody, { groups: [] });

      // --- Query User B (Cross-user isolation) ---
      const { status: statusB, body: bodyB } = await getHistoryRequest(
        { plannerToday: today },
        tokenB
      );
      assert.equal(statusB, 200);
      assert.equal(bodyB.groups.length, 1);
      assert.equal(bodyB.groups[0].date, dMinus3);
      assert.equal(bodyB.groups[0].items.length, 1);
      assert.deepEqual(bodyB.groups[0].items[0], {
        taskId: tUserBId,
        completionId: cUserBId,
        title: 'User B Secret History Snapshot',
        completedAt: `${dMinus3}T12:00:00.000Z`,
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
        `INSERT INTO task_schedules (id, task_id, schedule_type, start_date, end_date) VALUES ($1, $2, 'ONCE', $3, $3)`,
        [schedId, taskId, dMinus5]
      );

      // Complete it on dMinus5
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ plannerToday: today, completedDate: dMinus5, scheduleId: schedId, scheduledDate: dMinus5 })
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
      const h1 = await getHistoryRequest({ plannerToday: today, q: 'ONCE Task 1' }, tokenA);
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

      // Query History: now appears under original completed_date (dMinus5)
      const h2 = await getHistoryRequest({ plannerToday: today, q: 'ONCE Task 1' }, tokenA);
      assert.equal(h2.body.groups.length, 1);
      assert.equal(h2.body.groups[0].date, dMinus5);
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
      const h3 = await getHistoryRequest({ plannerToday: today, q: 'ONCE Task 1' }, tokenA);
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
        body: JSON.stringify({ plannerToday: today, completedDate: today })
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
      const h1 = await getHistoryRequest({ plannerToday: today, q: 'Later Task Alpha' }, tokenA);
      assert.equal(h1.body.groups.length, 1);
      assert.equal(h1.body.groups[0].date, today);
      assert.equal(h1.body.groups[0].items.length, 1);
      assert.equal(h1.body.groups[0].items[0].taskId, taskId);

      // 5. Unmark important
      const patchRes2 = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ isImportant: false })
      });
      assert.equal(patchRes2.status, 200);

      const h2 = await getHistoryRequest({ plannerToday: today, q: 'Later Task Alpha' }, tokenA);
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
        `INSERT INTO task_schedules (id, task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date) VALUES ($1, $2, 'INTERVAL_DAYS', $3, $4, 1, $3)`,
        [schedId, taskId, dMinus6, dMinus2]
      );

      // Complete on dMinus4
      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ plannerToday: today, completedDate: dMinus4, scheduleId: schedId, scheduledDate: dMinus4 })
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
      const h = await getHistoryRequest({ plannerToday: today, q: 'Daily Standup Recur' }, tokenA);
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
        body: JSON.stringify({ plannerToday: yesterday, completedDate: yesterday })
      });

      // Later edit title to "Edited Live Title"
      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'Edited Live Title' })
      });

      const h = await getHistoryRequest({ plannerToday: today, q: 'Original Title' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].date, yesterday);
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
         VALUES ($1, $2, NOW(), NULL, false)`,
        [taskId, dMinus5]
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

      const h = await getHistoryRequest({ plannerToday: today, q: 'Legacy Task' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].date, dMinus5);
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
         VALUES ($1, $2, NOW(), NULL, false)`,
        [taskId, dMinus5]
      );

      // PATCH title AND isImportant simultaneously
      await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({ title: 'New Live Title', isImportant: true })
      });

      const compDb = await pool.query('SELECT title_snapshot, is_important_snapshot FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb.rows[0].is_important_snapshot, true);
      assert.equal(compDb.rows[0].title_snapshot, 'Historical Locked Title');

      const h = await getHistoryRequest({ plannerToday: today, q: 'Historical Locked' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].date, dMinus5);
      assert.equal(h.body.groups[0].items[0].title, 'Historical Locked Title');
    });

    await sub.test('13: historical ONCE task creation, completion backfill, and history visibility lifecycle', async () => {
      const taskId = getValidId();
      // 1. Create historical ONCE task on dMinus4 while plannerToday is today
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Historical Backfilled ONCE Task',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: dMinus4 }
        })
      });
      assert.equal(createRes.status, 201);
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      // 2. Complete with historical completedDate = dMinus4
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: dMinus4,
          scheduleId,
          scheduledDate: dMinus4
        })
      });
      assert.equal(compRes.status, 200);

      // 3. Immediately appears in History on today grouped under dMinus4
      const h1 = await getHistoryRequest({ plannerToday: today, q: 'Backfilled' }, tokenA);
      assert.equal(h1.body.groups.length, 1);
      assert.equal(h1.body.groups[0].date, dMinus4);
      assert.equal(h1.body.groups[0].items.length, 1);
      assert.equal(h1.body.groups[0].items[0].title, 'Historical Backfilled ONCE Task');

      // 4. Undo completion removes it from History
      const undoRes = await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          scheduleId,
          scheduledDate: dMinus4
        })
      });
      assert.equal(undoRes.status, 200);

      const h2 = await getHistoryRequest({ plannerToday: today, q: 'Backfilled' }, tokenA);
      assert.equal(h2.body.groups.length, 0);
    });
  });

  await t.test('Same-day and early future completion History integration suite', async (sub) => {
    const getValidId = () => crypto.randomUUID();

    await sub.test('1. Important ONCE completed today appears in History today', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Important Today Once',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: today }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
          scheduleId,
          scheduledDate: today
        })
      });
      assert.equal(compRes.status, 200);

      const h = await getHistoryRequest({ plannerToday: today, q: 'Important Today Once' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].date, today);
      assert.equal(h.body.groups[0].items.length, 1);
      assert.equal(h.body.groups[0].items[0].taskId, taskId);
      assert.equal(h.body.groups[0].items[0].title, 'Important Today Once');
    });

    await sub.test('2-4. Important future ONCE completed early today appears in History today with original scheduledDate and grouped by completedDate', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Important Future Once Early',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: tomorrow }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      // Complete early on today for a task scheduled on tomorrow
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
          scheduleId,
          scheduledDate: tomorrow
        })
      });
      assert.equal(compRes.status, 200);

      // Verify DB row preserves original scheduledDate = tomorrow and completedDate = today
      const compDb = await pool.query('SELECT scheduled_date::text, completed_date::text FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compDb.rows[0].scheduled_date, tomorrow);
      assert.equal(compDb.rows[0].completed_date, today);

      // History returns it under completedDate group today
      const h = await getHistoryRequest({ plannerToday: today, q: 'Important Future Once Early' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].date, today);
      assert.equal(h.body.groups[0].items.length, 1);
      assert.equal(h.body.groups[0].items[0].taskId, taskId);
      assert.equal(h.body.groups[0].items[0].title, 'Important Future Once Early');
    });

    await sub.test('5. Historical Important completion still appears', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Historical Important Backfill',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: dMinus4 }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: dMinus4,
          scheduleId,
          scheduledDate: dMinus4
        })
      });

      const h = await getHistoryRequest({ plannerToday: today, q: 'Historical Important Backfill' }, tokenA);
      assert.equal(h.body.groups.length, 1);
      assert.equal(h.body.groups[0].date, dMinus4);
      assert.equal(h.body.groups[0].items.length, 1);
      assert.equal(h.body.groups[0].items[0].taskId, taskId);
    });

    await sub.test('6. Non-important completion remains excluded', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Non-important Today Task',
          isImportant: false,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: today }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
          scheduleId,
          scheduledDate: today
        })
      });

      const h = await getHistoryRequest({ plannerToday: today, q: 'Non-important Today Task' }, tokenA);
      assert.deepEqual(h.body.groups, []);
    });

    await sub.test('7. Recurring completion remains excluded', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Recurring Today Task',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'INTERVAL_DAYS', startDate: today, intervalDays: 1 }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
          scheduleId,
          scheduledDate: today
        })
      });

      const h = await getHistoryRequest({ plannerToday: today, q: 'Recurring Today Task' }, tokenA);
      assert.deepEqual(h.body.groups, []);
    });

    await sub.test('8. completedDate after plannerToday remains rejected', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Future Task Reject',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: dayAfterTomorrow }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: tomorrow,
          scheduleId,
          scheduledDate: dayAfterTomorrow
        })
      });
      assert.equal(compRes.status, 400);
      const body = await compRes.json();
      assert.match(body.error.message, /completedDate cannot be after plannerToday/);
    });

    await sub.test('9. existing History search behavior remains unchanged for today completions', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Searchable Target Task Today',
          isImportant: true,
          plannerToday: today,
          schedule: { type: 'ONCE', startDate: today }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
          scheduleId,
          scheduledDate: today
        })
      });

      // Match query
      const match = await getHistoryRequest({ plannerToday: today, q: 'sEaRcHaBlE tArGeT' }, tokenA);
      assert.equal(match.body.groups.length, 1);
      assert.equal(match.body.groups[0].items[0].title, 'Searchable Target Task Today');

      // Non-match query
      const noMatch = await getHistoryRequest({ plannerToday: today, q: 'unrelated string' }, tokenA);
      assert.deepEqual(noMatch.body.groups, []);
    });
  });
});
