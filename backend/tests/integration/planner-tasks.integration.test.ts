import { test, describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';
import { TasksRepository } from '../../src/planner/tasks/tasks.repository';

test('POST /tasks Integration Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();
  const tasksRepo = new TasksRepository();

  let user1: any;
  let user2: any;
  let token1: string;
  let token2: string;

  t.before(async () => {
    user1 = await createTestUserFixture({ emailSuffix: 'taskcreator1' });
    token1 = generateSessionToken();
    const hash1 = hashSessionToken(token1);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user1.id, hash1]
    );

    user2 = await createTestUserFixture({ emailSuffix: 'taskcreator2' });
    token2 = generateSessionToken();
    const hash2 = hashSessionToken(token2);
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user2.id, hash2]
    );
  });

  t.after(async () => {
    server.close();
    await pool.query('DELETE FROM tasks WHERE user_id = $1 OR user_id = $2', [user1.id, user2.id]);
    await deleteTestUserById(user1.id);
    await deleteTestUserById(user2.id);
  });

  const getValidId = () => '11111111-1111-4111-a111-' + Math.floor(Math.random() * 1000000000000).toString().padStart(12, '0');

  await t.test('returns 401 without auth', async () => {
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: getValidId(), title: 'T' }),
    });
    assert.equal(res.status, 401);
  });

  await t.test('returns 400 for bad UUID', async () => {
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({ id: 'not-a-uuid', title: 'T' }),
    });
    assert.equal(res.status, 400);
  });

  await t.test('creates a Later task without schedule or plannerToday', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({ id, title: 'Later Task', note: 'A note' }),
    });
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.task.id, id);
    assert.equal(body.task.title, 'Later Task');
    assert.equal(body.task.schedules.length, 0);
  });

  await t.test('creates historical ONCE task when startDate < plannerToday without reminder', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({
        id,
        title: 'Past Once Task',
        plannerToday: '2026-08-20',
        schedule: { type: 'ONCE', startDate: '2026-08-16' }
      }),
    });
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.task.id, id);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].type, 'ONCE');
    assert.equal(body.task.schedules[0].startDate, '2026-08-16');
  });

  await t.test('creates historical ONCE task with optional scheduledTime', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({
        id,
        title: 'Past Once Task With Time',
        plannerToday: '2026-08-20',
        schedule: { type: 'ONCE', startDate: '2026-08-16', scheduledTime: '14:30:00' }
      }),
    });
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.task.schedules[0].scheduledTime, '14:30:00');
  });

  await t.test('rejects creating historical ONCE task when reminder is provided with 400', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({
        id,
        title: 'Past Once Task With Reminder',
        plannerToday: '2026-08-20',
        schedule: { type: 'ONCE', startDate: '2026-08-16', scheduledTime: '14:30:00', reminderMinutesBefore: 15 }
      }),
    });
    assert.equal(res.status, 400);
    const body = await res.json();
    assert.match(body.error.message, /Reminders cannot be set for historical tasks/);
  });

  await t.test('rejects creating historical INTERVAL_DAYS task when startDate < plannerToday with 400', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({
        id,
        title: 'Past Interval Task',
        plannerToday: '2026-08-20',
        schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-16', intervalDays: 1 }
      }),
    });
    assert.equal(res.status, 400);
    const body = await res.json();
    assert.match(body.error.message, /Recurring schedules cannot start before plannerToday/);
  });

  await t.test('rejects creating historical WEEKDAYS task when startDate < plannerToday with 400', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({
        id,
        title: 'Past Weekdays Task',
        plannerToday: '2026-08-20',
        schedule: { type: 'WEEKDAYS', startDate: '2026-08-16', weekdaysMask: 31 }
      }),
    });
    assert.equal(res.status, 400);
    const body = await res.json();
    assert.match(body.error.message, /Recurring schedules cannot start before plannerToday/);
  });

  await t.test('creates a task with ONCE schedule atomically when startDate >= plannerToday', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({
        id,
        title: 'Once Task',
        plannerToday: '2026-08-20',
        schedule: { type: 'ONCE', startDate: '2026-08-20' }
      }),
    });
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.task.id, id);
    assert.equal(body.task.schedules.length, 1);
    assert.equal(body.task.schedules[0].type, 'ONCE');
    assert.equal(body.task.schedules[0].startDate, '2026-08-20');
    assert.equal(body.task.schedules[0].endDate, '2026-08-20');
  });

  await t.test('creates a task with INTERVAL_DAYS schedule', async () => {
    const id = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({ id, title: 'Interval Task', plannerToday: '2026-08-20', schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-20', intervalDays: 3 } }),
    });

    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.task.schedules[0].intervalDays, 3);
    assert.equal(body.task.schedules[0].intervalAnchorDate, '2026-08-20');
  });

  await t.test('returns 200 and existing task when re-submitting identical UUID (idempotency)', async () => {
    const id = getValidId();
    const payload = { id, title: 'Idempotent Task' };
    
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify(payload),
    });

    const res2 = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({ ...payload, title: 'Different Title' }),
    });
    assert.equal(res2.status, 200);
    const body = await res2.json();
    assert.equal(body.task.title, 'Idempotent Task');
  });

  await t.test('handles genuinely concurrent requests idempotently and safely', async () => {
    const id = getValidId();
    const payloadA = { id, title: 'Concurrent Winner', note: 'Payload A', plannerToday: '2026-08-20', schedule: { type: 'ONCE', startDate: '2026-08-20' } };
    const payloadB = { id, title: 'Concurrent Loser', note: 'Payload B', plannerToday: '2026-08-20', schedule: { type: 'ONCE', startDate: '2026-08-21' } };

    const reqA = fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify(payloadA),
    });

    const reqB = fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify(payloadB),
    });

    const [resA, resB] = await Promise.all([reqA, reqB]);

    assert.ok(resA.status === 201 || resA.status === 200, `resA status is ${resA.status}`);
    assert.ok(resB.status === 201 || resB.status === 200, `resB status is ${resB.status}`);

    const bodyA = await resA.json();
    const bodyB = await resB.json();

    // Both should describe exactly the same canonical state
    assert.equal(bodyA.task.id, id);
    assert.equal(bodyA.task.title, bodyB.task.title);
    assert.equal(bodyA.task.note, bodyB.task.note);
    assert.equal(bodyA.task.schedules.length, 1);
    assert.equal(bodyA.task.schedules[0].startDate, bodyB.task.schedules[0].startDate);

    // Verify exactly one row in DB
    const dbTasks = await pool.query('SELECT * FROM tasks WHERE id = $1', [id]);
    assert.equal(dbTasks.rows.length, 1);
    const dbSchedules = await pool.query('SELECT * FROM task_schedules WHERE task_id = $1', [id]);
    assert.equal(dbSchedules.rows.length, 1);

    // One should return 201, the other 200 (though we can't always guarantee exact timings, 
    // at minimum they shouldn't both be 201, nor should it crash).
    assert.notEqual(resA.status, resB.status);
  });

  await t.test('returns 409 Conflict if UUID is owned by another user', async () => {
    const id = getValidId();
    await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
      body: JSON.stringify({ id, title: 'User 1 Task' }),
    });

    const res2 = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token2}` },
      body: JSON.stringify({ id, title: 'User 2 Task' }),
    });
    assert.equal(res2.status, 409);
  });

  await t.test('rolls back completely if schedule violates database constraint', async () => {
    const id = getValidId();
    
    await assert.rejects(
      tasksRepo.createIdempotent(user1.id, {
        id,
        title: 'Valid title',
        schedule: { type: 'ONCE', startDate: '2026-08-18', endDate: '2026-08-17' } // endDate < startDate fails DB constraint
      })
    );

    const checkRes = await pool.query('SELECT * FROM tasks WHERE id = $1', [id]);
    assert.equal(checkRes.rows.length, 0); // Task should not exist, transaction rolled back
  });
});
