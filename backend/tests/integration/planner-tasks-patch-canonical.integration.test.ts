import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';

test('Planner Tasks PATCH Content-Only Canonical Response Integration Suite', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const pool = getPool();

  let user: any;
  let token: string;
  const today = new Date().toISOString().split('T')[0];

  t.before(async () => {
    user = await createTestUserFixture({ emailSuffix: 'patchcanonical' });
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

  const getValidId = () => '44444444-4444-4444-a444-' + Math.floor(Math.random() * 1000000000000).toString().padStart(12, '0');

  await t.test('A. ONCE task: post-completion Important toggle updates isImportantSnapshot while titleSnapshot/date/time remain frozen', async () => {
    const taskId = getValidId();
    // 1. Create ONCE task titled "Original", non-important
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Original',
        isImportant: false,
        plannerToday: today,
        schedule: { type: 'ONCE', startDate: today }
      })
    });
    assert.equal(createRes.status, 201);
    const created = await createRes.json() as any;
    const schedId = created.task.schedules[0].id;

    // 2. Complete it
    const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today,
        scheduleId: schedId,
        scheduledDate: today
      })
    });
    assert.equal(compRes.status, 200);
    const compInitial = await compRes.json() as any;
    assert.equal(compInitial.titleSnapshot, 'Original');
    assert.equal(compInitial.isImportantSnapshot, false);

    // 3. Capture DB values before PATCH
    const dbBefore = await pool.query(
      `SELECT id, completed_date::text, completed_at, title_snapshot, is_important_snapshot FROM task_completions WHERE id = $1`,
      [compInitial.id]
    );
    assert.equal(dbBefore.rows.length, 1);
    const rowBefore = dbBefore.rows[0];

    // 4. PATCH title to "Renamed" and isImportant=true
    const patch1 = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        title: 'Renamed',
        isImportant: true
      })
    });
    assert.equal(patch1.status, 200);
    const patch1Body = await patch1.json() as any;

    // 5. Prove: live task updated, completions updated and returned
    assert.equal(patch1Body.task.title, 'Renamed');
    assert.equal(patch1Body.task.isImportant, true);
    assert.equal(patch1Body.completions.length, 1);

    const compReturned1 = patch1Body.completions[0];
    assert.equal(compReturned1.id, compInitial.id);
    assert.equal(compReturned1.isImportantSnapshot, true);
    assert.equal(compReturned1.titleSnapshot, 'Original'); // Title snapshot frozen at completion time!
    assert.equal(compReturned1.completedDate, rowBefore.completed_date);
    const expectedCompletedAt = rowBefore.completed_at instanceof Date ? rowBefore.completed_at.toISOString() : new Date(rowBefore.completed_at).toISOString();
    assert.equal(compReturned1.completedAt, expectedCompletedAt);

    // Check DB directly
    const dbAfter1 = await pool.query(
      `SELECT title_snapshot, is_important_snapshot, completed_date::text, completed_at FROM task_completions WHERE id = $1`,
      [compInitial.id]
    );
    assert.equal(dbAfter1.rows[0].title_snapshot, 'Original');
    assert.equal(dbAfter1.rows[0].is_important_snapshot, true);

    // 6. PATCH isImportant=false
    const patch2 = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        isImportant: false
      })
    });
    assert.equal(patch2.status, 200);
    const patch2Body = await patch2.json() as any;

    assert.equal(patch2Body.task.isImportant, false);
    assert.equal(patch2Body.completions.length, 1);
    assert.equal(patch2Body.completions[0].isImportantSnapshot, false);
    assert.equal(patch2Body.completions[0].titleSnapshot, 'Original');
  });

  await t.test('B. Direct Later task: post-completion Important toggle updates isImportantSnapshot while titleSnapshot remains frozen', async () => {
    const taskId = getValidId();
    // 1. Create Later task titled "Original Later", non-important
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Original Later',
        isImportant: false
      })
    });
    assert.equal(createRes.status, 201);

    // 2. Complete it as Later
    const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today
      })
    });
    assert.equal(compRes.status, 200);
    const compInitial = await compRes.json() as any;
    assert.equal(compInitial.titleSnapshot, 'Original Later');
    assert.equal(compInitial.isImportantSnapshot, false);

    // 3. PATCH title to "Renamed Later" and isImportant=true
    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        title: 'Renamed Later',
        isImportant: true
      })
    });
    assert.equal(patchRes.status, 200);
    const patchBody = await patchRes.json() as any;

    assert.equal(patchBody.task.title, 'Renamed Later');
    assert.equal(patchBody.task.isImportant, true);
    assert.equal(patchBody.completions.length, 1);
    assert.equal(patchBody.completions[0].id, compInitial.id);
    assert.equal(patchBody.completions[0].isImportantSnapshot, true);
    assert.equal(patchBody.completions[0].titleSnapshot, 'Original Later');
  });

  await t.test('C. Recurring task: Important PATCH does not modify recurring completion or include it in affected completions', async () => {
    const taskId = getValidId();
    // 1. Create recurring INTERVAL_DAYS task
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Recurring Task',
        isImportant: false,
        plannerToday: today,
        schedule: { type: 'INTERVAL_DAYS', startDate: today, intervalDays: 2 }
      })
    });
    assert.equal(createRes.status, 201);
    const created = await createRes.json() as any;
    const schedId = created.task.schedules[0].id;

    // 2. Complete one occurrence
    const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today,
        scheduleId: schedId,
        scheduledDate: today
      })
    });
    assert.equal(compRes.status, 200);

    // 3. PATCH isImportant=true on recurring task
    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        isImportant: true
      })
    });
    assert.equal(patchRes.status, 200);
    const patchBody = await patchRes.json() as any;

    assert.equal(patchBody.task.isImportant, true);
    // Recurring completions are NOT history-eligible, so completions array is empty
    assert.equal(patchBody.completions.length, 0);

    // Verify in DB directly
    const dbComp = await pool.query(`SELECT is_important_snapshot, title_snapshot FROM task_completions WHERE task_id = $1`, [taskId]);
    assert.equal(dbComp.rows[0].is_important_snapshot, false);
    assert.equal(dbComp.rows[0].title_snapshot, null);
  });

  await t.test('D. Pure content edit: does not return affected completions or mutate completion metadata', async () => {
    const taskId = getValidId();
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Task For Pure Edit',
        note: 'Old note',
        isImportant: false
      })
    });
    assert.equal(createRes.status, 201);

    const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        plannerToday: today,
        completedDate: today
      })
    });
    assert.equal(compRes.status, 200);

    // Pure edit: title and note only, no isImportant change
    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        title: 'Updated Title Pure',
        note: 'Updated Note Pure'
      })
    });
    assert.equal(patchRes.status, 200);
    const patchBody = await patchRes.json() as any;

    assert.equal(patchBody.task.title, 'Updated Title Pure');
    assert.equal(patchBody.task.note, 'Updated Note Pure');
    assert.equal(patchBody.completions.length, 0);

    // DB completion metadata untouched
    const dbComp = await pool.query(`SELECT is_important_snapshot, title_snapshot FROM task_completions WHERE task_id = $1`, [taskId]);
    assert.equal(dbComp.rows[0].title_snapshot, 'Task For Pure Edit');
    assert.equal(dbComp.rows[0].is_important_snapshot, false);
  });

  await t.test('E. Schedule preservation: content-only PATCH preserves schedule ID, business fields, and timestamps', async () => {
    const taskId = getValidId();
    const createRes = await fetch(`${baseUrl}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        id: taskId,
        title: 'Preserve Sched Task',
        isImportant: false,
        plannerToday: today,
        schedule: {
          type: 'WEEKDAYS',
          startDate: today,
          weekdaysMask: 65,
          scheduledTime: '08:15:00',
          reminderMinutesBefore: 30
        }
      })
    });
    assert.equal(createRes.status, 201);

    const dbSchedBefore = await pool.query(
      `SELECT id, schedule_type, start_date::text, end_date::text, weekdays_mask, scheduled_time, reminder_minutes_before, created_at, updated_at
       FROM task_schedules WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbSchedBefore.rows.length, 1);
    const schedRowBefore = dbSchedBefore.rows[0];

    // Content-only PATCH
    const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        title: 'Renamed Sched Task',
        isImportant: true
      })
    });
    assert.equal(patchRes.status, 200);

    const dbSchedAfter = await pool.query(
      `SELECT id, schedule_type, start_date::text, end_date::text, weekdays_mask, scheduled_time, reminder_minutes_before, created_at, updated_at
       FROM task_schedules WHERE task_id = $1`,
      [taskId]
    );
    assert.equal(dbSchedAfter.rows.length, 1);
    const schedRowAfter = dbSchedAfter.rows[0];

    assert.equal(schedRowAfter.id, schedRowBefore.id);
    assert.equal(schedRowAfter.schedule_type, schedRowBefore.schedule_type);
    assert.equal(schedRowAfter.start_date, schedRowBefore.start_date);
    assert.equal(schedRowAfter.weekdays_mask, schedRowBefore.weekdays_mask);
    assert.equal(schedRowAfter.scheduled_time, schedRowBefore.scheduled_time);
    assert.equal(schedRowAfter.reminder_minutes_before, schedRowBefore.reminder_minutes_before);
    assert.equal(schedRowAfter.created_at.toISOString(), schedRowBefore.created_at.toISOString());
    assert.equal(schedRowAfter.updated_at.toISOString(), schedRowBefore.updated_at.toISOString());
  });
});
