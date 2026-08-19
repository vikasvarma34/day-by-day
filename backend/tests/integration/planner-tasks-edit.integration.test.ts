import { test, describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';
import crypto from 'node:crypto';

test('PATCH /tasks/:taskId Integration Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();

  let user1: any;
  let user2: any;
  let token1: string;
  let token2: string;

  t.before(async () => {
    user1 = await createTestUserFixture({ emailSuffix: 'edit1' });
    token1 = generateSessionToken();
    const hash1 = hashSessionToken(token1);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user1.id, hash1]
    );

    user2 = await createTestUserFixture({ emailSuffix: 'edit2' });
    token2 = generateSessionToken();
    const hash2 = hashSessionToken(token2);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user2.id, hash2]
    );
  });

  t.after(async () => {
    server.close();
    await pool.query(`
      DELETE FROM task_completions
      WHERE task_id IN (SELECT id FROM tasks WHERE user_id = $1 OR user_id = $2)
    `, [user1.id, user2.id]);
    await pool.query(`
      DELETE FROM task_schedules
      WHERE task_id IN (SELECT id FROM tasks WHERE user_id = $1 OR user_id = $2)
    `, [user1.id, user2.id]);
    await pool.query('DELETE FROM tasks WHERE user_id = $1 OR user_id = $2', [user1.id, user2.id]);

    await deleteTestUserById(user1.id);
    await deleteTestUserById(user2.id);
  });

  const getValidId = () => crypto.randomUUID();

  await t.test('rejects unauthenticated edit', async () => {
    const res = await fetch(`${baseUrl}/tasks/00000000-0000-4000-a000-000000000000`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ plannerToday: '2026-08-18', effectiveDate: '2026-08-18' }),
    });
    assert.equal(res.status, 401);
  });

  await t.test('rejects cross-user edit with 404', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'U1 Task', plannerToday: '2026-08-18' }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token2}` },
      body: JSON.stringify({ plannerToday: '2026-08-18', effectiveDate: '2026-08-18', title: 'Hacked' }),
    });
    assert.equal(res.status, 404);
  });

  await t.test('performs content-only Save atomically', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'Old', note: 'Old Note', plannerToday: '2026-08-18' }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        title: 'New',
        note: 'New Note',
        isImportant: true
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.title, 'New');
    assert.equal(body.task.note, 'New Note');
    assert.equal(body.task.isImportant, true);
  });

  await t.test('Later → ONCE rejects scheduling into past (< plannerToday)', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'Later Task' }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-19',
        effectiveDate: '2026-08-19',
        schedule: { type: 'ONCE', startDate: '2026-08-18' }
      }),
    });
    assert.equal(res.status, 400);
    const body = await (res.json() as any);
    assert.match(body.error.message, /Scheduled start date cannot be before plannerToday/);
  });

  await t.test('Later → ONCE schedule succeeds for today or future', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'Later' }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-18' }
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].startDate, '2026-08-18');
  });

  await t.test('ONCE reschedule rejects moving to date before plannerToday', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId,
        title: 'ONCE Today',
        plannerToday: '2026-08-19',
        schedule: { type: 'ONCE', startDate: '2026-08-19' }
      }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-19',
        effectiveDate: '2026-08-19',
        schedule: { type: 'ONCE', startDate: '2026-08-18' }
      }),
    });
    assert.equal(res.status, 400);
    const body = await (res.json() as any);
    assert.match(body.error.message, /Scheduled start date cannot be before plannerToday/);
  });

  await t.test('ONCE reschedule succeeds for today or future', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId,
        title: 'ONCE',
        plannerToday: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-18' }
      }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-19' }
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].startDate, '2026-08-19');
  });

  await t.test('content-only edit of a historical task with past schedule is not blocked', async () => {
    const taskId = getValidId();
    // Directly insert historical task with past schedule in DB
    await pool.query(`INSERT INTO tasks (id, user_id, title, note) VALUES ($1, $2, 'Historical Title', 'Old Note')`, [taskId, user1.id]);
    await pool.query(`INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) VALUES ($1, 'ONCE', '2020-01-01', '2020-01-01')`, [taskId]);

    // Content-only edit does not pass schedule
    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        title: 'Updated Historical Title',
        note: 'Updated Historical Note'
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.title, 'Updated Historical Title');
    assert.equal(body.task.note, 'Updated Historical Note');
    assert.equal(body.task.schedules[0].startDate, '2020-01-01');
  });

  await t.test('Recurring edit splits existing segment at effectiveDate', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Daily Task',
        plannerToday: '2026-08-10',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-10', intervalDays: 1 }
      }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-15',
        effectiveDate: '2026-08-15',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-15', intervalDays: 2 }
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    const schedules = body.task.schedules;
    assert.equal(schedules.length, 2);
    assert.equal(schedules[0].endDate, '2026-08-14');
    assert.equal(schedules[1].startDate, '2026-08-15');
  });

  await t.test('Later → INTERVAL_DAYS schedule', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'Later Task', plannerToday: '2026-08-18' }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-18', intervalDays: 3 }
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].type, 'INTERVAL_DAYS');
    assert.equal(body.task.schedules[0].intervalDays, 3);
  });

  await t.test('Later → WEEKDAYS schedule', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'Later Task', plannerToday: '2026-08-18' }),
    });

    const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        schedule: { type: 'WEEKDAYS', startDate: '2026-08-18', weekdaysMask: 62 }
      }),
    });
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].type, 'WEEKDAYS');
    assert.equal(body.task.schedules[0].weekdaysMask, 62);
  });

  await t.test('Atomic rollback: forced DB constraint failure rolls back content', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId, title: 'T2', plannerToday: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-18' }
      }),
    });

    const schedRes = await pool.query('SELECT id FROM task_schedules WHERE task_id = $1', [taskId]);
    await pool.query(`INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot) VALUES ($1, $2, '2026-08-18', '2026-08-18', '2026-08-18 00:00:00+00', false)`, [taskId, schedRes.rows[0].id]);

    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        title: 'Hacked Title',
        schedule: { type: 'ONCE', startDate: '2026-08-19' }
      }),
    });
    assert.equal(patchRes.status, 409); // Conflict

    const dbRes = await pool.query('SELECT title FROM tasks WHERE id = $1', [taskId]);
    assert.equal(dbRes.rows[0].title, 'T2');
  });

  await t.test('Completed ONCE reschedule returns conflict', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId, title: 'T', plannerToday: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-18' }
      }),
    });
    const schedRes = await pool.query('SELECT id FROM task_schedules WHERE task_id = $1', [taskId]);
    await pool.query(`INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot) VALUES ($1, $2, '2026-08-18', '2026-08-18', '2026-08-18 00:00:00+00', false)`, [taskId, schedRes.rows[0].id]);

    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-19' }
      }),
    });
    assert.equal(patchRes.status, 409);
  });

  await t.test('Active recurring segment completion conflict at or after D fails edit', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId, title: 'Recur', plannerToday: '2026-08-10',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-10', intervalDays: 1 }
      }),
    });
    const schedRes = await pool.query('SELECT id FROM task_schedules WHERE task_id = $1', [taskId]);
    await pool.query(`INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot) VALUES ($1, $2, '2026-08-15', '2026-08-15', '2026-08-15 00:00:00+00', false)`, [taskId, schedRes.rows[0].id]);

    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-12',
        effectiveDate: '2026-08-12',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-12', intervalDays: 2 }
      }),
    });
    assert.equal(patchRes.status, 409);
  });

  await t.test('Superseded future-segment completion conflict fails edit', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId, title: 'Recur', plannerToday: '2026-08-10',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-10', intervalDays: 1 }
      }),
    });
    const schedRes = await pool.query('SELECT id FROM task_schedules WHERE task_id = $1', [taskId]);
    await pool.query(`UPDATE task_schedules SET start_date = '2026-08-15' WHERE id = $1`, [schedRes.rows[0].id]);
    await pool.query(`INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot) VALUES ($1, $2, '2026-08-16', '2026-08-16', '2026-08-16 00:00:00+00', false)`, [taskId, schedRes.rows[0].id]);

    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-12',
        effectiveDate: '2026-08-12',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-12', intervalDays: 2 }
      }),
    });
    assert.equal(patchRes.status, 409);
  });

  await t.test('Interval anchor logic: preserve vs reset', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId, title: 'Recur', plannerToday: '2026-08-10',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-10', intervalDays: 3 }
      }),
    });

    let patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-15',
        effectiveDate: '2026-08-15',
        title: 'New Title',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-15', intervalDays: 3 }
      }),
    });
    assert.equal(patchRes.status, 200);
    let body = await (patchRes.json() as any);
    assert.equal(body.task.schedules[1].intervalAnchorDate, '2026-08-10');

    patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-20',
        effectiveDate: '2026-08-20',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-20', intervalDays: 4 }
      }),
    });
    assert.equal(patchRes.status, 200);
    body = await (patchRes.json() as any);
    assert.equal(body.task.schedules[2].intervalAnchorDate, '2026-08-20');
  });

  await t.test('Finite preserved-segment validity: leaves no empty WEEKDAYS', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId, title: 'Recur', plannerToday: '2026-08-10', // Monday
        schedule: { type: 'WEEKDAYS', startDate: '2026-08-10', weekdaysMask: 1 } // Mon
      }),
    });
    let patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-11',
        effectiveDate: '2026-08-11',
        schedule: { type: 'WEEKDAYS', startDate: '2026-08-11', weekdaysMask: 2 } // Tue
      }),
    });
    assert.equal(patchRes.status, 200);
    let body = await (patchRes.json() as any);
    assert.equal(body.task.schedules.length, 2);

    const taskId2 = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        id: taskId2, title: 'Recur2', plannerToday: '2026-08-11', // Tuesday
        schedule: { type: 'WEEKDAYS', startDate: '2026-08-11', weekdaysMask: 8 } // Thursday
      }),
    });
    patchRes = await fetch(`${baseUrl}/tasks/${taskId2}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-12',
        effectiveDate: '2026-08-12',
        schedule: { type: 'WEEKDAYS', startDate: '2026-08-12', weekdaysMask: 16 } // Friday
      }),
    });
    assert.equal(patchRes.status, 200);
    body = await (patchRes.json() as any);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].startDate, '2026-08-12');
  });

  await t.test('Concurrency: FOR UPDATE lock serializes edits', async () => {
    const taskId = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({ id: taskId, title: 'Original', plannerToday: '2026-08-18' }),
    });

    const lockClient = await pool.connect();
    await lockClient.query('BEGIN');
    await lockClient.query('SELECT * FROM tasks WHERE id = $1 FOR UPDATE', [taskId]);

    const patchPromise = fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token1}` },
      body: JSON.stringify({
        plannerToday: '2026-08-18',
        effectiveDate: '2026-08-18',
        title: 'Updated After Lock'
      }),
    });

    let patchFinished = false;
    patchPromise.then(() => { patchFinished = true; });

    await new Promise(r => setTimeout(r, 200));
    assert.equal(patchFinished, false);

    await lockClient.query('UPDATE tasks SET title = $1 WHERE id = $2', ['Updated By Lock', taskId]);
    await lockClient.query('COMMIT');
    lockClient.release();

    const res = await patchPromise;
    assert.equal(res.status, 200);
    const body = await (res.json() as any);
    assert.equal(body.task.title, 'Updated After Lock');
  });
});
