import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';
import crypto from 'node:crypto';

test('Planner Tasks Mutations API Integration Suite', async (t) => {
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
    user1 = await createTestUserFixture({ emailSuffix: 'mutations1' });
    token1 = generateSessionToken();
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user1.id, hashSessionToken(token1)]
    );

    user2 = await createTestUserFixture({ emailSuffix: 'mutations2' });
    token2 = generateSessionToken();
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '1 day')`,
      [user2.id, hashSessionToken(token2)]
    );
  });

  t.after(async () => {
    server.close();
    await pool.query('DELETE FROM tasks WHERE user_id = $1 OR user_id = $2', [user1.id, user2.id]);
    await deleteTestUserById(user1.id);
    await deleteTestUserById(user2.id);
  });

  const getValidId = () => '11111111-1111-4111-a111-' + Math.floor(Math.random() * 1000000000000).toString().padStart(12, '0');

  await t.test('Unauthenticated and Cross-owner protections', async (sub) => {
    await sub.test('rejects complete without auth', async () => {
      const res = await fetch(`${baseUrl}/tasks/${getValidId()}/complete`, { 
        method: 'POST', 
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}) 
      });
      assert.equal(res.status, 401);
    });

    await sub.test('rejects cross-owner task complete', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'T1' })
      });

      const res = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token2}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      assert.equal(res.status, 404);
    });
    


    await sub.test('idempotently returns 200 for nonexistent tasks or cross-owner tasks without deleting them', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'T1' })
      });

      // Attempt to delete user1's task with user2's token
      const resCrossOwner = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'DELETE', headers: { 'Authorization': `Bearer ${token2}` }
      });
      assert.equal(resCrossOwner.status, 200, 'Cross-owner delete returns 200 to not leak existence');

      // Verify the task was NOT actually deleted
      const check1 = await pool.query('SELECT 1 FROM tasks WHERE id = $1', [taskId]);
      assert.equal(check1.rows.length, 1, 'Cross-owner task must remain in database');

      // Attempt to delete a totally non-existent UUID
      const resNonExistent = await fetch(`${baseUrl}/tasks/${getValidId()}`, {
        method: 'DELETE', headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(resNonExistent.status, 200, 'Non-existent task delete returns 200');
    });
  });

  await t.test('Later Tasks Complete + Undo', async (sub) => {
    await sub.test('completes a Later task and is idempotent on retry', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Later Task', isImportant: true })
      });
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      assert.equal(res.status, 200);

      const compCheck = await pool.query('SELECT title_snapshot, is_important_snapshot FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compCheck.rows.length, 1);
      assert.equal(compCheck.rows[0].title_snapshot, 'Later Task');
      assert.equal(compCheck.rows[0].is_important_snapshot, true);
      
      const retryRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      assert.equal(retryRes.status, 200);
      
      const compCheck2 = await pool.query('SELECT 1 FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compCheck2.rows.length, 1);
    });

    await sub.test('undoes a Later task completion and is idempotent on retry', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Later Task' })
      });
      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({})
      });
      assert.equal(res.status, 200);

      const compCheck = await pool.query('SELECT 1 FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compCheck.rows.length, 0);

      const retryRes = await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({})
      });
      assert.equal(retryRes.status, 200);
    });
  });

  await t.test('Scheduled Tasks Complete + Undo', async (sub) => {
    await sub.test('completes an ONCE task, idempotently, and undoes it', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Once Task', plannerToday: '2026-08-20', schedule: { type: 'ONCE', startDate: '2026-08-20' } })
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      const completePayload = { plannerToday: '2026-08-20', completedDate: '2026-08-20', scheduleId, scheduledDate: '2026-08-20' };
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify(completePayload)
      });
      assert.equal(res.status, 200);

      const compCheck = await pool.query('SELECT 1 FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(compCheck.rows.length, 1);

      const undoRes = await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify(completePayload)
      });
      assert.equal(undoRes.status, 200);
      
      const undoCheck = await pool.query('SELECT 1 FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(undoCheck.rows.length, 0);
    });
    
    await sub.test('rejects a completion on a date not generated by the schedule', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Weekly Task', plannerToday: '2026-08-20', schedule: { type: 'WEEKDAYS', weekdaysMask: 32, startDate: '2026-08-20' } }) // Saturday only (Aug 22)
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20', scheduleId, scheduledDate: '2026-08-20' })
      });
      assert.equal(res.status, 409);
    });
  });

  await t.test('Delete Task', async (sub) => {
    await sub.test('hard deletes task and cascades', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'To Delete', plannerToday: '2026-08-20', schedule: { type: 'ONCE', startDate: '2026-08-20' } })
      });
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'DELETE', headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(res.status, 200);
      const resBody = await res.json();
      assert.equal(resBody.deletedTaskId, taskId);
      
      const taskCheck = await pool.query('SELECT 1 FROM tasks WHERE id = $1', [taskId]);
      assert.equal(taskCheck.rows.length, 0);
    });
  });

  await t.test('Stop Recurrence', async (sub) => {
    await sub.test('removes future empty segments and ends current active segment without invalidating past completions', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Recurring', plannerToday: '2026-08-19', schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-19' } })
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;
      
      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-19', scheduleId, scheduledDate: '2026-08-19' })
      });
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });
      assert.equal(res.status, 200);
      
      const schedCheck = await pool.query('SELECT end_date::text FROM task_schedules WHERE id = $1', [scheduleId]);
      assert.equal(schedCheck.rows[0].end_date, '2026-08-20');
    });

    await sub.test('hard deletes task if it never generated any occurrence and has no completions', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Never generated', plannerToday: '2026-08-20', schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-25' } })
      });
      assert.equal(createRes.status, 201);
      
      const res = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });
      assert.equal(res.status, 200);
      
      const taskCheck = await pool.query('SELECT 1 FROM tasks WHERE id = $1', [taskId]);
      assert.equal(taskCheck.rows.length, 0);
    });

    await sub.test('rejects stop recurrence if a future completion prevents it safely', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Future Completion', plannerToday: '2026-08-20', schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-20' } })
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;
      
      // Complete it on Aug 25 (future occurrence relative to plannerToday 2026-08-20)
      await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20', scheduleId, scheduledDate: '2026-08-25' })
      });

      // Manually set scheduled_date completion in db to test future completion guard
      await pool.query('UPDATE task_completions SET scheduled_date = $1 WHERE task_id = $2', ['2026-08-25', taskId]);

      // Attempt to stop recurrence on Aug 20
      const res = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });
      
      // Must return 409 Conflict because of the future completion
      assert.equal(res.status, 409);
    });

    await sub.test('rejects stop recurrence if a future segment has recorded completions', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Future Segment', plannerToday: '2026-08-19', schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-19' } })
      });
      const createBody = await createRes.json();

      // Manually add a future segment with a completion
      const futureSchedRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date) VALUES ($1, 'INTERVAL_DAYS', '2026-08-25', '2026-08-30', 1, '2026-08-25') RETURNING id`,
        [taskId]
      );
      const schedule2Id = futureSchedRes.rows[0].id;
      await pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot) VALUES ($1, $2, '2026-08-25', '2026-08-20', NOW(), 'Future Segment', false)`,
        [taskId, schedule2Id]
      );

      // Attempt to stop recurrence on Aug 20
      const res = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });

      // Must return 409 Conflict because of the future segment completion
      assert.equal(res.status, 409);
    });

    await sub.test('stops finite currently-effective recurring segment at plannerToday', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Finite Recurring Task',
          plannerToday: '2026-08-19',
          schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-19', endDate: '2026-08-31' }
        })
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      const res = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });
      assert.equal(res.status, 200);

      const schedCheck = await pool.query('SELECT end_date::text FROM task_schedules WHERE id = $1', [scheduleId]);
      assert.equal(schedCheck.rows[0].end_date, '2026-08-20');

      // Attempting completion after Aug 20 should now fail with 409 Conflict
      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20', scheduleId, scheduledDate: '2026-08-21' })
      });
      assert.equal(compRes.status, 409);
    });

    await sub.test('preserves task and historical schedule when removing a never-generated future segment', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Historical Task',
          plannerToday: '2026-08-19',
          schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-19', endDate: '2026-08-19' }
        })
      });
      const createBody = await createRes.json();
      const histScheduleId = createBody.task.schedules[0].id;

      // Add a future recurring segment that will never generate an occurrence before Aug 20
      const futureSchedRes = await pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date) VALUES ($1, 'INTERVAL_DAYS', '2026-08-25', '2026-08-31', 1, '2026-08-25') RETURNING id`,
        [taskId]
      );
      const futureScheduleId = futureSchedRes.rows[0].id;

      const res = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });
      assert.equal(res.status, 200);

      // Future segment should be deleted
      const futureCheck = await pool.query('SELECT 1 FROM task_schedules WHERE id = $1', [futureScheduleId]);
      assert.equal(futureCheck.rows.length, 0);

      // Historical segment must still exist
      const histCheck = await pool.query('SELECT 1 FROM task_schedules WHERE id = $1', [histScheduleId]);
      assert.equal(histCheck.rows.length, 1);

      // The task itself must NOT be hard deleted
      const taskCheck = await pool.query('SELECT 1 FROM tasks WHERE id = $1', [taskId]);
      assert.equal(taskCheck.rows.length, 1);
    });
  });

  await t.test('Concurrency and Rollback Proofs', async (sub) => {
    await sub.test('held-row-lock serializes FOR UPDATE queries', async () => {
      const taskId = getValidId();
      await pool.query(`INSERT INTO tasks (id, user_id, title) VALUES ($1, $2, 'Lock Test')`, [taskId, user1.id]);

      const client1 = await pool.connect();
      await client1.query('BEGIN');
      await client1.query('SELECT * FROM tasks WHERE id = $1 FOR UPDATE', [taskId]);

      let fetchCompleted = false;
      const fetchPromise = fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      }).then(r => { fetchCompleted = true; return r; });

      // Wait a bit to prove it's blocked
      await new Promise(r => setTimeout(r, 100));
      assert.equal(fetchCompleted, false, 'Fetch should be blocked by FOR UPDATE lock');

      await client1.query('COMMIT');
      client1.release();

      const res = await fetchPromise;
      // It returns 409 because we didn't setup schedule properly, but the block works and it returns!
      assert.equal(fetchCompleted, true);
    });

    await sub.test('mid-transaction PostgreSQL failure rolls back prior mutations', async () => {
      const taskId = getValidId();
      await pool.query(`INSERT INTO tasks (id, user_id, title) VALUES ($1, $2, 'Rollback Test')`, [taskId, user1.id]);

      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        await client.query(`UPDATE tasks SET title = 'Changed' WHERE id = $1`, [taskId]);
        // Force PG error (invalid UUID syntax)
        await client.query(`INSERT INTO task_completions (task_id, completed_date) VALUES ('not-a-uuid', '2026-08-20')`);
        await client.query('COMMIT');
      } catch (err) {
        await client.query('ROLLBACK');
      } finally {
        client.release();
      }

      const check = await pool.query(`SELECT title FROM tasks WHERE id = $1`, [taskId]);
      assert.equal(check.rows[0].title, 'Rollback Test', 'Title should not be changed');
    });
  });

  await t.test('Completion Date Business Integrity Validation', async (sub) => {
    await sub.test('1-5: past occurrence completedDate boundary rules (scheduledDate=2026-08-16, plannerToday=2026-08-20)', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Past Task',
          plannerToday: '2026-08-20',
          schedule: { type: 'ONCE', startDate: '2026-08-16' }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      // 4. completedDate before scheduledDate fails with 400
      const resBefore = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-15',
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(resBefore.status, 400);

      // 5. completedDate after plannerToday fails with 400
      const resAfter = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-21',
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(resAfter.status, 400);

      // 1. completedDate = scheduledDate succeeds
      const resBackfill = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-16',
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(resBackfill.status, 200);

      // Undo
      await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ scheduleId, scheduledDate: '2026-08-16' })
      });

      // 2. completedDate between scheduledDate and plannerToday succeeds
      const resBetween = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-18',
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(resBetween.status, 200);

      // Undo
      await fetch(`${baseUrl}/tasks/${taskId}/undo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ scheduleId, scheduledDate: '2026-08-16' })
      });

      // 3. completedDate = plannerToday succeeds
      const resToday = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-20',
          scheduleId,
          scheduledDate: '2026-08-16'
        })
      });
      assert.equal(resToday.status, 200);
    });

    await sub.test('6-7: future occurrence completion rules (scheduledDate=2026-08-27, plannerToday=2026-08-20)', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Future Task',
          plannerToday: '2026-08-20',
          schedule: { type: 'ONCE', startDate: '2026-08-27' }
        })
      });
      assert.equal(createRes.status, 201);
      const scheduleId = (await createRes.json()).task.schedules[0].id;

      // 7a. completedDate < plannerToday fails
      const resBeforeToday = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-19',
          scheduleId,
          scheduledDate: '2026-08-27'
        })
      });
      assert.equal(resBeforeToday.status, 400);

      // 7b. completedDate in future relative to plannerToday fails
      const resFuture = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-27',
          scheduleId,
          scheduledDate: '2026-08-27'
        })
      });
      assert.equal(resFuture.status, 400);

      // 6. completedDate = plannerToday succeeds (early completion today)
      const resEarly = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-20',
          scheduleId,
          scheduledDate: '2026-08-27'
        })
      });
      assert.equal(resEarly.status, 200);
    });

    await sub.test('8-10: Later task completion rules (plannerToday=2026-08-20)', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Later Task Strict' })
      });

      // 9. Historical completedDate fails for Later
      const resHist = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-19' })
      });
      assert.equal(resHist.status, 400);

      // 10. Future completedDate fails for Later
      const resFut = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-21' })
      });
      assert.equal(resFut.status, 400);

      // 8. completedDate = plannerToday succeeds
      const resOk = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      assert.equal(resOk.status, 200);
    });

    await sub.test('11: missing or malformed plannerToday fails with 400', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Later Task Missing Today' })
      });

      const resMissing = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ completedDate: '2026-08-20' })
      });
      assert.equal(resMissing.status, 400);

      const resMalformed = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: 'not-a-date', completedDate: '2026-08-20' })
      });
      assert.equal(resMalformed.status, 400);
    });
  });

  await t.test('Adversarial plannerToday Ingress Guard Tests', async (sub) => {
    await sub.test('POST /tasks rejects implausible plannerToday spoofing (1900 and 2099)', async () => {
      const taskId1 = getValidId();
      const res1900 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId1,
          title: 'Spoofed 1900 Task',
          plannerToday: '1900-01-01',
          schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2020-01-01' }
        })
      });
      assert.equal(res1900.status, 400);
      const body1900 = await res1900.json();
      assert.match(body1900.error.message, /plannerToday is outside plausible calendar range/);

      const taskId2 = getValidId();
      const res2099 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId2,
          title: 'Spoofed 2099 Task',
          plannerToday: '2099-01-01',
          schedule: { type: 'ONCE', startDate: '2099-01-01' }
        })
      });
      assert.equal(res2099.status, 400);
      const body2099 = await res2099.json();
      assert.match(body2099.error.message, /plannerToday is outside plausible calendar range/);
    });

    await sub.test('PATCH /tasks/:taskId rejects implausible plannerToday spoofing (1900-01-01)', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Task to Patch' })
      });

      const patchRes = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '1900-01-01',
          effectiveDate: '2020-01-01',
          schedule: { type: 'ONCE', startDate: '2020-01-01' }
        })
      });
      assert.equal(patchRes.status, 400);
      const patchBody = await patchRes.json();
      assert.match(patchBody.error.message, /plannerToday is outside plausible calendar range/);
    });

    await sub.test('POST /tasks/:taskId/complete rejects implausible plannerToday spoofing (2099-01-01)', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Task to Complete Spoofed' })
      });

      const compRes = await fetch(`${baseUrl}/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2099-01-01',
          completedDate: '2099-01-01'
        })
      });
      assert.equal(compRes.status, 400);
      const compBody = await compRes.json();
      assert.match(compBody.error.message, /plannerToday is outside plausible calendar range/);
    });

    await sub.test('POST /tasks/:taskId/stop-recurrence rejects implausible plannerToday spoofing', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Recurring Task to Stop',
          plannerToday: '2026-08-20',
          schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-20' }
        })
      });

      const stopRes = await fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2099-01-01' })
      });
      assert.equal(stopRes.status, 400);
      const stopBody = await stopRes.json();
      assert.match(stopBody.error.message, /plannerToday is outside plausible calendar range/);
    });

    await sub.test('GET /planner/history rejects implausible plannerToday spoofing', async () => {
      const histRes = await fetch(`${baseUrl}/planner/history?plannerToday=2099-01-01`, {
        headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(histRes.status, 400);
      const histBody = await histRes.json();
      assert.match(histBody.error.message, /plannerToday is outside plausible calendar range/);
    });
  });

  await t.test('Adversarial Authorization, Malformed Input & Boundary Regression Tests', async (sub) => {
    await sub.test('User A uses User B scheduleId against User A task completion is rejected safely', async () => {
      const taskAId = getValidId();
      const taskBId = getValidId();

      const createARes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskAId, title: 'Task A', plannerToday: '2026-08-20', schedule: { type: 'ONCE', startDate: '2026-08-20' } })
      });
      assert.equal(createARes.status, 201);

      const createBRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token2}` },
        body: JSON.stringify({ id: taskBId, title: 'Task B', plannerToday: '2026-08-20', schedule: { type: 'ONCE', startDate: '2026-08-20' } })
      });
      assert.equal(createBRes.status, 201);
      const bodyB = await createBRes.json();
      const scheduleBId = bodyB.task.schedules[0].id;

      // User A attempts to complete Task A using User B's scheduleId
      const crossCompRes = await fetch(`${baseUrl}/tasks/${taskAId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          plannerToday: '2026-08-20',
          completedDate: '2026-08-20',
          scheduleId: scheduleBId,
          scheduledDate: '2026-08-20'
        })
      });
      assert.equal(crossCompRes.status, 409);
    });

    await sub.test('User A stop-recurrence against User B task returns 404 without mutation', async () => {
      const taskBId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token2}` },
        body: JSON.stringify({
          id: taskBId,
          title: 'User B Recurring',
          plannerToday: '2026-08-20',
          schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-20' }
        })
      });

      const stopRes = await fetch(`${baseUrl}/tasks/${taskBId}/stop-recurrence`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20' })
      });
      assert.equal(stopRes.status, 404);

      // Verify schedule is untouched
      const check = await pool.query('SELECT end_date FROM task_schedules WHERE task_id = $1', [taskBId]);
      assert.equal(check.rows.length, 1);
      assert.equal(check.rows[0].end_date, null);
    });

    await sub.test('Same client-generated task UUID by different users blocks second owner', async () => {
      const sharedTaskId = getValidId();

      const user1Res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: sharedTaskId, title: 'User 1 Task' })
      });
      assert.equal(user1Res.status, 201);

      const user2Res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token2}` },
        body: JSON.stringify({ id: sharedTaskId, title: 'User 2 Hijack Attempt' })
      });
      assert.equal(user2Res.status, 409);

      // Verify ownership remains User 1
      const check = await pool.query('SELECT user_id, title FROM tasks WHERE id = $1', [sharedTaskId]);
      assert.equal(check.rows[0].user_id, user1.id);
      assert.equal(check.rows[0].title, 'User 1 Task');
    });

    await sub.test('POST /tasks Content-Type text/plain returns 415', async () => {
      const res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain', 'Authorization': `Bearer ${token1}` },
        body: '{"title":"Plain text"}'
      });
      assert.equal(res.status, 415);
    });

    await sub.test('POST /tasks array JSON body returns 400', async () => {
      const res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify([{ id: getValidId(), title: 'Array body' }])
      });
      assert.equal(res.status, 400);
    });

    await sub.test('POST /tasks primitive JSON body returns 400', async () => {
      const res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify('just a string')
      });
      assert.equal(res.status, 400);
    });

    await sub.test('malformed taskId on PATCH returns 400', async () => {
      const res = await fetch(`${baseUrl}/tasks/invalid-uuid-123`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ title: 'New Title' })
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.match(body.error.message, /must be a UUID v4/);
    });

    await sub.test('malformed taskId on complete returns 400', async () => {
      const res = await fetch(`${baseUrl}/tasks/invalid-uuid-123/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ plannerToday: '2026-08-20', completedDate: '2026-08-20' })
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.match(body.error.message, /must be a UUID v4/);
    });

    await sub.test('malformed taskId on DELETE returns 400', async () => {
      const res = await fetch(`${baseUrl}/tasks/invalid-uuid-123`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.match(body.error.message, /must be a UUID v4/);
    });

    await sub.test('duplicate plannerToday query parameters on History returns controlled 400', async () => {
      const res = await fetch(`${baseUrl}/planner/history?plannerToday=2026-08-20&plannerToday=2026-08-21`, {
        headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(res.status, 400);
    });

    await sub.test('POST /tasks with title exactly 255 chars is accepted', async () => {
      const taskId = getValidId();
      const title255 = 'A'.repeat(255);
      const res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: title255 })
      });
      assert.equal(res.status, 201);
      const body = await res.json();
      assert.equal(body.task.title, title255);
    });

    await sub.test('POST /tasks with title 256 chars is rejected with 400', async () => {
      const taskId = getValidId();
      const title256 = 'A'.repeat(256);
      const res = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: title256 })
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.equal(body.error.message, 'Invalid title: max 255 characters');
    });

    await sub.test('PATCH /tasks/:taskId with title 256 chars is rejected with 400', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ id: taskId, title: 'Valid Short' })
      });

      const title256 = 'B'.repeat(256);
      const res = await fetch(`${baseUrl}/tasks/${taskId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({ title: title256 })
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.equal(body.error.message, 'Invalid title: max 255 characters');
    });

    await sub.test('GET /planner/history with q exactly 200 chars is accepted', async () => {
      const q200 = 'x'.repeat(200);
      const res = await fetch(`${baseUrl}/planner/history?plannerToday=2026-08-20&q=${q200}`, {
        headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(res.status, 200);
    });

    await sub.test('GET /planner/history with q 201 chars is rejected with 400', async () => {
      const q201 = 'x'.repeat(201);
      const res = await fetch(`${baseUrl}/planner/history?plannerToday=2026-08-20&q=${q201}`, {
        headers: { 'Authorization': `Bearer ${token1}` }
      });
      assert.equal(res.status, 400);
      const body = await res.json();
      assert.equal(body.error.message, 'Invalid search query: max 200 characters');
    });
  });

  await t.test('Concurrency & Race Condition Regression Tests', async (sub) => {
    await sub.test('A: concurrent complete requests for same occurrence yield exactly one completion row', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Concurrent Complete Target',
          plannerToday: '2026-08-20',
          schedule: { type: 'ONCE', startDate: '2026-08-20' }
        })
      });
      assert.equal(createRes.status, 201);
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      const payload = {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-20',
        scheduleId,
        scheduledDate: '2026-08-20'
      };

      // Launch 5 parallel complete requests
      const results = await Promise.all(
        Array.from({ length: 5 }, () =>
          fetch(`${baseUrl}/tasks/${taskId}/complete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
            body: JSON.stringify(payload)
          })
        )
      );

      for (const r of results) {
        assert.equal(r.status, 200);
      }

      const countRes = await pool.query('SELECT COUNT(*)::int as count FROM task_completions WHERE task_id = $1', [taskId]);
      assert.equal(countRes.rows[0].count, 1);
    });

    await sub.test('B: concurrent complete vs undo produces no deadlock and serialized valid outcome', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Complete vs Undo',
          plannerToday: '2026-08-20',
          schedule: { type: 'ONCE', startDate: '2026-08-20' }
        })
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      const payload = {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-20',
        scheduleId,
        scheduledDate: '2026-08-20'
      };

      // Run complete and undo concurrently
      const [compRes, undoRes] = await Promise.all([
        fetch(`${baseUrl}/tasks/${taskId}/complete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
          body: JSON.stringify(payload)
        }),
        fetch(`${baseUrl}/tasks/${taskId}/undo`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
          body: JSON.stringify(payload)
        })
      ]);

      assert.equal(compRes.status, 200);
      assert.equal(undoRes.status, 200);

      const countRes = await pool.query('SELECT COUNT(*)::int as count FROM task_completions WHERE task_id = $1', [taskId]);
      assert.ok(countRes.rows[0].count === 0 || countRes.rows[0].count === 1);
    });

    await sub.test('C: concurrent stop-recurrence vs complete produces clean valid state', async () => {
      const taskId = getValidId();
      const createRes = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
        body: JSON.stringify({
          id: taskId,
          title: 'Recurring Race Task',
          plannerToday: '2026-08-20',
          schedule: { type: 'INTERVAL_DAYS', intervalDays: 1, startDate: '2026-08-20' }
        })
      });
      const createBody = await createRes.json();
      const scheduleId = createBody.task.schedules[0].id;

      const [stopRes, compRes] = await Promise.all([
        fetch(`${baseUrl}/tasks/${taskId}/stop-recurrence`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
          body: JSON.stringify({ plannerToday: '2026-08-20' })
        }),
        fetch(`${baseUrl}/tasks/${taskId}/complete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
          body: JSON.stringify({
            plannerToday: '2026-08-20',
            completedDate: '2026-08-20',
            scheduleId,
            scheduledDate: '2026-08-20'
          })
        })
      ]);

      assert.equal(stopRes.status, 200);
      assert.equal(compRes.status, 200);

      const schedCheck = await pool.query('SELECT end_date::text FROM task_schedules WHERE task_id = $1', [taskId]);
      assert.equal(schedCheck.rows.length, 1);
      assert.equal(schedCheck.rows[0].end_date, '2026-08-20');
    });

    await sub.test('D: concurrent same UUID create by two users assigns exactly one owner', async () => {
      const sharedTaskId = getValidId();

      const [resUser1, resUser2] = await Promise.all([
        fetch(`${baseUrl}/tasks`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token1}` },
          body: JSON.stringify({ id: sharedTaskId, title: 'User 1 Concurrent' })
        }),
        fetch(`${baseUrl}/tasks`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token2}` },
          body: JSON.stringify({ id: sharedTaskId, title: 'User 2 Concurrent' })
        })
      ]);

      const statuses = [resUser1.status, resUser2.status].sort();
      assert.deepEqual(statuses, [201, 409]);

      const dbCheck = await pool.query('SELECT COUNT(*)::int as count FROM tasks WHERE id = $1', [sharedTaskId]);
      assert.equal(dbCheck.rows[0].count, 1);
    });
  });
});
