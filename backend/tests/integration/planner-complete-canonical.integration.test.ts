import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';

test('Planner Complete Canonical & Idempotent Integration Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();

  let user: any;
  let token: string;
  const today = new Date().toISOString().split('T')[0];

  t.before(async () => {
    user = await createTestUserFixture({ emailSuffix: 'completecanonical' });
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
    await pool.end();
  });

  const getValidId = () => '22222222-2222-4222-a222-' + Math.floor(Math.random() * 1000000000000).toString().padStart(12, '0');

  await t.test('Initial Complete & Idempotent Retry for Scheduled ONCE Task', async () => {
    const taskId = getValidId();
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Canonical Once Task',
        isImportant: true,
        plannerToday: today,
        schedule: { type: 'ONCE', startDate: today }
      })
    });
    assert.equal(createRes.status, 201);
    const createBody = await createRes.json() as any;
    const scheduleId = createBody.task.schedules[0].id;

    // 1. Initial Complete
    const completeRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today,
        scheduleId,
        scheduledDate: today
      })
    });
    assert.equal(completeRes.status, 200);
    const compBody = await completeRes.json() as any;

    // Check Postgres row
    const dbRes = await pool.query(
      `SELECT id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot
       FROM task_completions WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbRes.rows.length, 1);
    const row = dbRes.rows[0];

    // Assert response values equal the actual persisted row
    assert.equal(compBody.id, row.id);
    assert.equal(compBody.taskId, row.task_id);
    assert.equal(compBody.scheduleId, row.schedule_id);
    assert.equal(compBody.scheduledDate, row.scheduled_date);
    assert.equal(compBody.completedDate, row.completed_date);
    const expectedCompletedAt = row.completed_at instanceof Date ? row.completed_at.toISOString() : new Date(row.completed_at).toISOString();
    assert.equal(compBody.completedAt, expectedCompletedAt);
    assert.equal(compBody.titleSnapshot, row.title_snapshot);
    assert.equal(compBody.titleSnapshot, 'Canonical Once Task');
    assert.equal(compBody.isImportantSnapshot, row.is_important_snapshot);
    assert.equal(compBody.isImportantSnapshot, true);

    // 2. Idempotent Retry for the same occurrence
    const retryRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today,
        scheduleId,
        scheduledDate: today
      })
    });
    assert.equal(retryRes.status, 200);
    const retryBody = await retryRes.json() as any;

    // Prove: no second completion row is created
    const dbRes2 = await pool.query(
      `SELECT id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot
       FROM task_completions WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbRes2.rows.length, 1);

    // Prove: returned id, completedAt, titleSnapshot, isImportantSnapshot match original persisted values
    assert.equal(retryBody.id, compBody.id);
    assert.equal(retryBody.completedAt, compBody.completedAt);
    assert.equal(retryBody.titleSnapshot, compBody.titleSnapshot);
    assert.equal(retryBody.isImportantSnapshot, compBody.isImportantSnapshot);
  });

  await t.test('Initial Complete & Idempotent Retry for Direct Later Task (null scheduleId/scheduledDate)', async () => {
    const taskId = getValidId();
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Canonical Later Task',
        isImportant: false
      })
    });
    assert.equal(createRes.status, 201);

    // 1. Initial Complete
    const completeRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today
      })
    });
    assert.equal(completeRes.status, 200);
    const compBody = await completeRes.json() as any;

    // Check Postgres row
    const dbRes = await pool.query(
      `SELECT id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot
       FROM task_completions WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbRes.rows.length, 1);
    const row = dbRes.rows[0];

    // Assert response values equal actual persisted row
    assert.equal(compBody.id, row.id);
    assert.equal(compBody.taskId, row.task_id);
    assert.equal(compBody.scheduleId, null);
    assert.equal(compBody.scheduledDate, null);
    assert.equal(compBody.completedDate, row.completed_date);
    const expectedCompletedAt = row.completed_at instanceof Date ? row.completed_at.toISOString() : new Date(row.completed_at).toISOString();
    assert.equal(compBody.completedAt, expectedCompletedAt);
    assert.equal(compBody.titleSnapshot, row.title_snapshot);
    assert.equal(compBody.titleSnapshot, 'Canonical Later Task');
    assert.equal(compBody.isImportantSnapshot, row.is_important_snapshot);
    assert.equal(compBody.isImportantSnapshot, false);

    // 2. Idempotent Retry
    const retryRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today
      })
    });
    assert.equal(retryRes.status, 200);
    const retryBody = await retryRes.json() as any;

    // Prove: no second completion row is created
    const dbRes2 = await pool.query(
      `SELECT id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot
       FROM task_completions WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbRes2.rows.length, 1);

    // Prove: identity and fields match original
    assert.equal(retryBody.id, compBody.id);
    assert.equal(retryBody.completedAt, compBody.completedAt);
    assert.equal(retryBody.titleSnapshot, compBody.titleSnapshot);
    assert.equal(retryBody.isImportantSnapshot, compBody.isImportantSnapshot);
  });
});
