import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool, closePool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';

test('Planner Tasks Create Canonical Response Integration Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();

  let user: any;
  let token: string;
  const today = new Date().toISOString().split('T')[0];

  t.before(async () => {
    user = await createTestUserFixture({ emailSuffix: 'createcanonical' });
    token = generateSessionToken();
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user.id, hashSessionToken(token)]
    );
  });

  t.after(async () => {
    server.close();
    if (user?.id) {
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [user.id]);
      await deleteTestUserById(user.id);
    }
    await closePool();
  });

  const getValidId = () => '33333333-3333-4333-a333-' + Math.floor(Math.random() * 1000000000000).toString().padStart(12, '0');

  await t.test('1. New Later task create response timestamps equal persisted PostgreSQL values', async () => {
    const taskId = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Canonical Later Task',
        note: 'Some note',
        isImportant: true
      })
    });
    assert.equal(res.status, 201);
    const body = await res.json() as any;

    const dbRes = await pool.query(
      `SELECT id, title, note, is_important, created_at, updated_at FROM tasks WHERE id = $1`,
      [taskId]
    );
    assert.equal(dbRes.rows.length, 1);
    const dbRow = dbRes.rows[0];

    assert.equal(body.task.id, taskId);
    assert.equal(body.task.title, 'Canonical Later Task');
    assert.equal(body.task.note, 'Some note');
    assert.equal(body.task.isImportant, true);
    assert.equal(body.task.schedules.length, 0);

    const expectedCreatedAt = dbRow.created_at instanceof Date ? dbRow.created_at.toISOString() : new Date(dbRow.created_at).toISOString();
    const expectedUpdatedAt = dbRow.updated_at instanceof Date ? dbRow.updated_at.toISOString() : new Date(dbRow.updated_at).toISOString();

    assert.equal(body.task.createdAt, expectedCreatedAt);
    assert.equal(body.task.updatedAt, expectedUpdatedAt);
  });

  await t.test('2. Scheduled create response schedule ID/timestamps equal persisted rows', async () => {
    const taskId = getValidId();
    const res = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Canonical Scheduled Task',
        isImportant: false,
        plannerToday: today,
        schedule: {
          type: 'WEEKDAYS',
          startDate: today,
          weekdaysMask: 31,
          scheduledTime: '10:30:00',
          reminderMinutesBefore: 15
        }
      })
    });
    assert.equal(res.status, 201);
    const body = await res.json() as any;

    const dbTaskRes = await pool.query(`SELECT created_at, updated_at FROM tasks WHERE id = $1`, [taskId]);
    assert.equal(dbTaskRes.rows.length, 1);
    const dbTask = dbTaskRes.rows[0];

    const dbSchedRes = await pool.query(
      `SELECT id, schedule_type, start_date::text, weekdays_mask, scheduled_time, reminder_minutes_before, created_at, updated_at
       FROM task_schedules WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbSchedRes.rows.length, 1);
    const dbSched = dbSchedRes.rows[0];

    assert.equal(body.task.id, taskId);
    assert.equal(body.task.schedules.length, 1);

    const schedule = body.task.schedules[0];
    assert.equal(schedule.id, dbSched.id);
    assert.equal(schedule.type, 'WEEKDAYS');
    assert.equal(schedule.startDate, today);
    assert.equal(schedule.weekdaysMask, 31);
    assert.equal(schedule.scheduledTime, '10:30:00');
    assert.equal(schedule.reminderMinutesBefore, 15);

    const expectedSchedCreatedAt = dbSched.created_at instanceof Date ? dbSched.created_at.toISOString() : new Date(dbSched.created_at).toISOString();
    const expectedSchedUpdatedAt = dbSched.updated_at instanceof Date ? dbSched.updated_at.toISOString() : new Date(dbSched.updated_at).toISOString();
    assert.equal(schedule.createdAt, expectedSchedCreatedAt);
    assert.equal(schedule.updatedAt, expectedSchedUpdatedAt);
  });

  await t.test('3 & 4. Same-ID retry returns the same canonical task/schedule IDs, original timestamps, and creates no duplicates', async () => {
    const taskId = getValidId();
    // First creation
    const res1 = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Original Title',
        plannerToday: today,
        schedule: { type: 'ONCE', startDate: today }
      })
    });
    assert.equal(res1.status, 201);
    const body1 = await res1.json() as any;

    // Retry with same UUID
    const res2 = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Ignored Different Title',
        plannerToday: today,
        schedule: { type: 'ONCE', startDate: today }
      })
    });
    assert.equal(res2.status, 200);
    const body2 = await res2.json() as any;

    // Prove: returned aggregate equals original
    assert.equal(body2.task.id, body1.task.id);
    assert.equal(body2.task.title, 'Original Title');
    assert.equal(body2.task.createdAt, body1.task.createdAt);
    assert.equal(body2.task.updatedAt, body1.task.updatedAt);
    assert.equal(body2.task.schedules.length, 1);
    assert.equal(body2.task.schedules[0].id, body1.task.schedules[0].id);
    assert.equal(body2.task.schedules[0].createdAt, body1.task.schedules[0].createdAt);
    assert.equal(body2.task.schedules[0].updatedAt, body1.task.schedules[0].updatedAt);

    // Prove: no duplicate rows in DB
    const dbTasks = await pool.query('SELECT 1 FROM tasks WHERE id = $1', [taskId]);
    assert.equal(dbTasks.rows.length, 1);
    const dbSchedules = await pool.query('SELECT 1 FROM task_schedules WHERE task_id = $1', [taskId]);
    assert.equal(dbSchedules.rows.length, 1);
  });
});
