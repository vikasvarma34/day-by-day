import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool, closePool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';
import { WEEKDAY_MASKS } from '../../src/planner/recurrence';

test('GET /planner/days/:date Integration Suite', async (t) => {
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
    userA = await createTestUserFixture({ emailSuffix: 'planner.usera' });
    userB = await createTestUserFixture({ emailSuffix: 'planner.userb' });
    createdUserIds.push(userA.id, userB.id);

    // Create active session for userA
    tokenA = generateSessionToken();
    const tokenHashA = hashSessionToken(tokenA);
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
      [userA.id, tokenHashA, expiresAt]
    );

    // Create active session for userB
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

  async function getDayRequest(date: string, token?: string) {
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const response = await fetch(`${baseUrl}/planner/days/${date}`, { headers });
    const body = await response.json();
    return { status: response.status, body };
  }

  await t.test('Authentication: unauthenticated request is rejected with 401 UNAUTHORIZED', async () => {
    const { status, body } = await getDayRequest('2026-08-18');
    assert.equal(status, 401);
    assert.equal(body.error.code, 'UNAUTHORIZED');
  });

  await t.test('Date validation: malformed or impossible date rejected with 400 BAD_REQUEST', async () => {
    const malformed = ['2026-8-18', 'abc', '2026-02-30', '2025-02-29', '2026-13-01'];
    for (const d of malformed) {
      const { status, body } = await getDayRequest(d, tokenA);
      assert.equal(status, 400, `Date ${d} should be rejected with 400`);
      assert.equal(body.error.code, 'BAD_REQUEST');
    }
  });

  await t.test('Date validation: valid leap date (2024-02-29) is accepted', async () => {
    const { status, body } = await getDayRequest('2024-02-29', tokenA);
    assert.equal(status, 200);
    assert.equal(body.date, '2024-02-29');
    assert.deepEqual(body.timed, []);
    assert.deepEqual(body.anytime, []);
    assert.deepEqual(body.completed, []);
  });

  await t.test('Comprehensive planner read: ONCE, INTERVAL_DAYS, WEEKDAYS, sections, completions, and isolation', async () => {
    // 1. User A - ONCE task occurring on 2026-08-18 (Tuesday) with time 09:00:00 (Incomplete -> Timed)
    const taskOnceRes = await pool.query(
      `INSERT INTO tasks (user_id, title, note, is_important) VALUES ($1, 'Doctor Appointment', 'Bring reports', true) RETURNING id`,
      [userA.id]
    );
    const taskOnceId = taskOnceRes.rows[0].id;
    const schedOnceRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, scheduled_time, reminder_minutes_before)
       VALUES ($1, 'ONCE', '2026-08-18', '2026-08-18', '09:00:00', 30) RETURNING id`,
      [taskOnceId]
    );
    const schedOnceId = schedOnceRes.rows[0].id;

    // 2. User A - ONCE task on another date 2026-08-19 (must NOT appear on 2026-08-18)
    const taskOtherDateRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Dentist Tomorrow') RETURNING id`,
      [userA.id]
    );
    await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, scheduled_time)
       VALUES ($1, 'ONCE', '2026-08-19', '2026-08-19', '10:00:00')`,
      [taskOtherDateRes.rows[0].id]
    );

    // 3. User A - INTERVAL_DAYS task (Every 2 days from anchor 2026-08-16, start 2026-08-17).
    // 2026-08-18 is 2 days from 2026-08-16 -> Occurs! (No time -> Anytime)
    const taskIntervalRes = await pool.query(
      `INSERT INTO tasks (user_id, title, note, is_important) VALUES ($1, 'Water Plants', 'Garden and balcony', false) RETURNING id`,
      [userA.id]
    );
    const taskIntervalId = taskIntervalRes.rows[0].id;
    const schedIntervalRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_days, interval_anchor_date)
       VALUES ($1, 'INTERVAL_DAYS', '2026-08-17', 2, '2026-08-16') RETURNING id`,
      [taskIntervalId]
    );
    const schedIntervalId = schedIntervalRes.rows[0].id;

    // 4. User A - WEEKDAYS task (Tue=2, Thu=8 -> mask = 10), open-ended, scheduled_time = 18:30:00.
    // 2026-08-18 is Tuesday -> Occurs!
    // Completed on 2026-08-18 -> Must appear in completed, NOT timed.
    const taskWeekdaysRes = await pool.query(
      `INSERT INTO tasks (user_id, title, is_important) VALUES ($1, 'Evening Jog', false) RETURNING id`,
      [userA.id]
    );
    const taskWeekdaysId = taskWeekdaysRes.rows[0].id;
    const schedWeekdaysRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, scheduled_time, weekdays_mask)
       VALUES ($1, 'WEEKDAYS', '2026-08-01', '18:30:00', ${WEEKDAY_MASKS.TUESDAY | WEEKDAY_MASKS.THURSDAY}) RETURNING id`,
      [taskWeekdaysId]
    );
    const schedWeekdaysId = schedWeekdaysRes.rows[0].id;

    // Insert completion for the Tuesday occurrence
    const completionRes = await pool.query(
      `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, $2, '2026-08-18', '2026-08-18', '2026-08-18T19:00:00.000Z', false) RETURNING id, completed_at`,
      [taskWeekdaysId, schedWeekdaysId]
    );
    const completionId = completionRes.rows[0].id;

    // 5. User A - WEEKDAYS task for Wednesday only (mask = 4).
    // 2026-08-18 is Tuesday -> Must NOT occur on 2026-08-18.
    const taskWedRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Wednesday Team Sync') RETURNING id`,
      [userA.id]
    );
    await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, weekdays_mask)
       VALUES ($1, 'WEEKDAYS', '2026-08-01', ${WEEKDAY_MASKS.WEDNESDAY})`,
      [taskWedRes.rows[0].id]
    );

    // 6. User A - Later task (no schedule row) -> Must NOT appear.
    await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Someday Project Idea')`,
      [userA.id]
    );

    // 7. User A - Timed task at 07:00:00 (Early Morning) to test ascending sort order with Doctor Appointment (09:00:00)
    const taskEarlyRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Morning Meditation') RETURNING id`,
      [userA.id]
    );
    const taskEarlyId = taskEarlyRes.rows[0].id;
    const schedEarlyRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, scheduled_time)
       VALUES ($1, 'ONCE', '2026-08-18', '2026-08-18', '07:00:00') RETURNING id`,
      [taskEarlyId]
    );
    const schedEarlyId = schedEarlyRes.rows[0].id;

    // 8. User B - Task for 2026-08-18 -> Cross-user isolation must hide this from User A
    const taskUserBRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'User B Secret Task') RETURNING id`,
      [userB.id]
    );
    await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date)
       VALUES ($1, 'ONCE', '2026-08-18', '2026-08-18')`,
      [taskUserBRes.rows[0].id]
    );

    // Perform the query for User A
    const { status, body } = await getDayRequest('2026-08-18', tokenA);
    assert.equal(status, 200);
    assert.equal(body.date, '2026-08-18');

    // Verify Timed section: 2 items, ordered ascending (07:00:00, then 09:00:00)
    assert.equal(body.timed.length, 2);
    assert.deepEqual(body.timed[0], {
      taskId: taskEarlyId,
      scheduleId: schedEarlyId,
      title: 'Morning Meditation',
      note: null,
      isImportant: false,
      scheduleType: 'ONCE',
      scheduledDate: '2026-08-18',
      scheduledTime: '07:00:00',
      reminderMinutesBefore: null,
      completion: null,
    });
    assert.deepEqual(body.timed[1], {
      taskId: taskOnceId,
      scheduleId: schedOnceId,
      title: 'Doctor Appointment',
      note: 'Bring reports',
      isImportant: true,
      scheduleType: 'ONCE',
      scheduledDate: '2026-08-18',
      scheduledTime: '09:00:00',
      reminderMinutesBefore: 30,
      completion: null,
    });

    // Verify Anytime section: 1 item (Water Plants)
    assert.equal(body.anytime.length, 1);
    assert.deepEqual(body.anytime[0], {
      taskId: taskIntervalId,
      scheduleId: schedIntervalId,
      title: 'Water Plants',
      note: 'Garden and balcony',
      isImportant: false,
      scheduleType: 'INTERVAL_DAYS',
      scheduledDate: '2026-08-18',
      scheduledTime: null,
      reminderMinutesBefore: null,
      completion: null,
    });

    // Verify Completed section: 1 item (Evening Jog), with completion object
    assert.equal(body.completed.length, 1);
    assert.deepEqual(body.completed[0], {
      taskId: taskWeekdaysId,
      scheduleId: schedWeekdaysId,
      title: 'Evening Jog',
      note: null,
      isImportant: false,
      scheduleType: 'WEEKDAYS',
      scheduledDate: '2026-08-18',
      scheduledTime: '18:30:00',
      reminderMinutesBefore: null,
      completion: {
        id: completionId,
        completedDate: '2026-08-18',
        completedAt: new Date('2026-08-18T19:00:00.000Z').toISOString(),
      },
    });

    // Verify User B only sees their task, not User A's
    const userBResponse = await getDayRequest('2026-08-18', tokenB);
    assert.equal(userBResponse.status, 200);
    assert.equal(userBResponse.body.timed.length, 0);
    assert.equal(userBResponse.body.completed.length, 0);
    assert.equal(userBResponse.body.anytime.length, 1);
    assert.equal(userBResponse.body.anytime[0].title, 'User B Secret Task');
  });

  await t.test('Date validation: year zero (0000-01-01) returns 400 BAD_REQUEST', async () => {
    const { status, body } = await getDayRequest('0000-01-01', tokenA);
    assert.equal(status, 400);
    assert.equal(body.error.code, 'BAD_REQUEST');
  });

  await t.test('Schedule boundaries: finite recurring schedule ending before requested date does not appear', async () => {
    // Create task with finite recurring schedule ending on 2026-08-17
    const taskRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Expired Sprint Task') RETURNING id`,
      [userA.id]
    );
    const taskId = taskRes.rows[0].id;
    await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date)
       VALUES ($1, 'INTERVAL_DAYS', '2026-08-01', '2026-08-17', 1, '2026-08-01')`,
      [taskId]
    );

    // Query on 2026-08-18
    const { status, body } = await getDayRequest('2026-08-18', tokenA);
    assert.equal(status, 200);

    const allTaskIds = [
      ...body.timed.map((i: any) => i.taskId),
      ...body.anytime.map((i: any) => i.taskId),
      ...body.completed.map((i: any) => i.taskId),
    ];
    assert.equal(allTaskIds.includes(taskId), false, 'Finite schedule ending before requested date must not appear');
  });

  await t.test('Completion isolation: completion on a different scheduled date is not attached to requested occurrence', async () => {
    // Create task with daily schedule occurring on both 2026-08-17 and 2026-08-18
    const taskRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Daily Standup Call') RETURNING id`,
      [userA.id]
    );
    const taskId = taskRes.rows[0].id;
    const schedRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, scheduled_time, interval_days, interval_anchor_date)
       VALUES ($1, 'INTERVAL_DAYS', '2026-08-01', '09:30:00', 1, '2026-08-01') RETURNING id`,
      [taskId]
    );
    const scheduleId = schedRes.rows[0].id;

    // Complete the task specifically for yesterday (2026-08-17)
    await pool.query(
      `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, $2, '2026-08-17', '2026-08-17', '2026-08-17T09:35:00.000Z', false)`,
      [taskId, scheduleId]
    );

    // Query for today (2026-08-18)
    const { status, body } = await getDayRequest('2026-08-18', tokenA);
    assert.equal(status, 200);

    const occurrence = body.timed.find((i: any) => i.taskId === taskId);
    assert.ok(occurrence, 'Occurrence for 2026-08-18 must be present in timed section');
    assert.equal(occurrence.completion, null, 'Completion from 2026-08-17 must NOT be attached to 2026-08-18 occurrence');

    const completedOccurrence = body.completed.find((i: any) => i.taskId === taskId);
    assert.equal(completedOccurrence, undefined, 'Task must not appear in completed section for 2026-08-18');
  });
});
