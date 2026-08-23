import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/pool';
import { createTestUserFixture, deleteTestUserById, getTestDate } from './auth-test-fixtures';
import { generateSessionToken, hashSessionToken } from '../../src/security/session';
import { RefreshRepository } from '../../src/planner/refresh/refresh.repository';
import { PlannerSnapshot } from '../../src/planner/refresh/refresh.types';

test('GET /planner/refresh Integration Suite', async (t) => {
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
  const futureEnd = getTestDate(40);

  const createdUserIds: string[] = [];

  t.before(async () => {
    userA = await createTestUserFixture({ emailSuffix: 'refresh.usera' });
    userB = await createTestUserFixture({ emailSuffix: 'refresh.userb' });
    createdUserIds.push(userA.id, userB.id);

    tokenA = generateSessionToken();
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '30 days')`,
      [userA.id, hashSessionToken(tokenA)]
    );

    tokenB = generateSessionToken();
    await pool.query(
      `INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES ($1, $2, NOW() + INTERVAL '30 days')`,
      [userB.id, hashSessionToken(tokenB)]
    );
  });

  t.after(async () => {
    server.close();
    for (const id of createdUserIds) {
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [id]);
      await deleteTestUserById(id);
    }
  });

  const getValidId = () => crypto.randomUUID();

  await t.test('Authentication and Owner Isolation', async (sub) => {
    await sub.test('rejects unauthenticated request with 401', async () => {
      const res = await fetch(`${baseUrl}/planner/refresh`);
      assert.equal(res.status, 401);
    });

    await sub.test('isolates snapshot data by authenticated user', async () => {
      // Create task for User A
      const taskIdA = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskIdA,
          title: 'User A Secret Task',
          note: 'Confidential note',
        }),
      });

      // Request snapshot for User A
      const resA = await fetch(`${baseUrl}/planner/refresh`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      });
      assert.equal(resA.status, 200);
      const snapshotA: PlannerSnapshot = await resA.json();
      assert.equal(snapshotA.tasks.length, 1);
      assert.equal(snapshotA.tasks[0].id, taskIdA);
      assert.equal(snapshotA.tasks[0].title, 'User A Secret Task');

      // Request snapshot for User B
      const resB = await fetch(`${baseUrl}/planner/refresh`, {
        headers: { Authorization: `Bearer ${tokenB}` },
      });
      assert.equal(resB.status, 200);
      const snapshotB: PlannerSnapshot = await resB.json();
      assert.equal(snapshotB.tasks.length, 0);
      assert.equal(snapshotB.schedules.length, 0);
      assert.equal(snapshotB.completions.length, 0);

      // Cleanup
      await pool.query('DELETE FROM tasks WHERE id = $1', [taskIdA]);
    });
  });

  await t.test('Canonical Planner Records and Internal Coherence', async (sub) => {
    await sub.test('returns full snapshot with coherent tasks, schedules, and completions', async () => {
      // 1. Task with ONCE schedule, completed
      const taskId1 = getValidId();
      const createRes1 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId1,
          title: 'Doctor Appointment',
          note: 'Checkup',
          isImportant: true,
          plannerToday: today,
          schedule: {
            type: 'ONCE',
            startDate: today,
            scheduledTime: '14:30:00',
            reminderMinutesBefore: 30,
          },
        }),
      });
      assert.equal(createRes1.status, 201);
      const createBody1 = await createRes1.json();
      const schedId1 = createBody1.task.schedules[0].id;

      // Complete Task 1
      const compRes1 = await fetch(`${baseUrl}/tasks/${taskId1}/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
          scheduleId: schedId1,
          scheduledDate: today,
        }),
      });
      assert.equal(compRes1.status, 200);

      // 2. Task with INTERVAL_DAYS recurring schedule
      const taskId2 = getValidId();
      const createRes2 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId2,
          title: 'Water Plants',
          plannerToday: today,
          schedule: {
            type: 'INTERVAL_DAYS',
            startDate: today,
            endDate: futureEnd,
            intervalDays: 3,
          },
        }),
      });
      assert.equal(createRes2.status, 201);

      // 3. Task with WEEKDAYS recurring schedule
      const taskId3 = getValidId();
      const createRes3 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId3,
          title: 'Daily Standup',
          plannerToday: today,
          schedule: {
            type: 'WEEKDAYS',
            startDate: today,
            weekdaysMask: 31, // Mon-Fri
            scheduledTime: '09:00:00',
          },
        }),
      });
      assert.equal(createRes3.status, 201);

      // 4. Later Task (unscheduled, incomplete)
      const taskId4 = getValidId();
      const createRes4 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId4,
          title: 'Read Book Later',
        }),
      });
      assert.equal(createRes4.status, 201);

      // 5. Directly completed Later Task
      const taskId5 = getValidId();
      const createRes5 = await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId5,
          title: 'Directly Completed Later Task',
          isImportant: true,
        }),
      });
      assert.equal(createRes5.status, 201);
      const compRes5 = await fetch(`${baseUrl}/tasks/${taskId5}/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          plannerToday: today,
          completedDate: today,
        }),
      });
      assert.equal(compRes5.status, 200);

      // Request snapshot
      const res = await fetch(`${baseUrl}/planner/refresh`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      });
      assert.equal(res.status, 200);
      const snapshot: PlannerSnapshot = await res.json();

      // Check tasks
      assert.equal(snapshot.tasks.length, 5);
      const taskIds = new Set(snapshot.tasks.map((t) => t.id));
      assert.ok(taskIds.has(taskId1));
      assert.ok(taskIds.has(taskId2));
      assert.ok(taskIds.has(taskId3));
      assert.ok(taskIds.has(taskId4));
      assert.ok(taskIds.has(taskId5));

      // Check schedules
      assert.equal(snapshot.schedules.length, 3);
      const scheduleIds = new Set(snapshot.schedules.map((s) => s.id));
      for (const s of snapshot.schedules) {
        assert.ok(taskIds.has(s.taskId), `Schedule ${s.id} references valid task`);
      }

      // Check completions
      assert.equal(snapshot.completions.length, 2);
      for (const c of snapshot.completions) {
        assert.ok(taskIds.has(c.taskId), `Completion ${c.id} references valid task`);
        if (c.scheduleId !== null) {
          assert.ok(
            scheduleIds.has(c.scheduleId),
            `Completion ${c.id} references valid schedule`
          );
        }
      }

      // Cleanup
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userA.id]);
    });
  });

  await t.test('Plain Planner DATE/TIME Semantics', async (sub) => {
    await sub.test('preserves plain DATE and TIME format strings without timezone shifting', async () => {
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId,
          title: 'Timezone Check Task',
          plannerToday: today,
          schedule: {
            type: 'ONCE',
            startDate: today,
            scheduledTime: '15:45:00',
            reminderMinutesBefore: 15,
          },
        }),
      });

      const res = await fetch(`${baseUrl}/planner/refresh`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      });
      assert.equal(res.status, 200);
      const snapshot: PlannerSnapshot = await res.json();

      assert.equal(snapshot.tasks.length, 1);
      assert.equal(snapshot.schedules.length, 1);
      const sched = snapshot.schedules[0];

      assert.equal(sched.startDate, today);
      assert.equal(sched.endDate, today);
      assert.equal(sched.scheduledTime, '15:45:00');
      assert.equal(sched.reminderMinutesBefore, 15);

      // Verify timestamp formatting
      assert.match(snapshot.tasks[0].createdAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
      assert.match(snapshot.tasks[0].updatedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
      assert.match(sched.createdAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);

      // Cleanup
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userA.id]);
    });
  });

  await t.test('Empty Snapshot', async (sub) => {
    await sub.test('returns empty arrays for user with no data', async () => {
      const res = await fetch(`${baseUrl}/planner/refresh`, {
        headers: { Authorization: `Bearer ${tokenB}` },
      });
      assert.equal(res.status, 200);
      const snapshot: PlannerSnapshot = await res.json();

      assert.deepEqual(snapshot, {
        tasks: [],
        schedules: [],
        completions: [],
      });
    });
  });

  await t.test('Streaming Capability', async (sub) => {
    await sub.test('streams chunked response that can be read incrementally', async () => {
      // Clean state for User A
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userA.id]);

      // Create a task
      const taskId = getValidId();
      await fetch(`${baseUrl}/tasks`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: taskId,
          title: 'Streaming Test Task',
        }),
      });

      const res = await fetch(`${baseUrl}/planner/refresh`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      });
      assert.equal(res.status, 200);
      assert.equal(res.headers.get('content-type'), 'application/json; charset=utf-8');

      // Read response body chunks directly from stream
      assert.ok(res.body, 'Response body stream exists');
      const reader = res.body.getReader();
      const chunks: Uint8Array[] = [];

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (value) chunks.push(value);
      }

      const rawText = Buffer.concat(chunks).toString('utf-8');
      const parsed = JSON.parse(rawText);

      assert.equal(parsed.tasks.length, 1);
      assert.equal(parsed.tasks[0].id, taskId);
      assert.equal(parsed.tasks[0].title, 'Streaming Test Task');
      assert.deepEqual(parsed.schedules, []);
      assert.deepEqual(parsed.completions, []);

      // Cleanup
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userA.id]);
    });
  });

  await t.test('PostgreSQL REPEATABLE READ Snapshot Isolation', async (sub) => {
    await sub.test('proves REPEATABLE READ consistency across concurrent database mutations', async () => {
      // Ensure clean state for User A
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userA.id]);

      // Setup initial baseline: Task 1 + Schedule 1
      const taskId1 = getValidId();
      await pool.query(
        `INSERT INTO tasks (id, user_id, title) VALUES ($1, $2, 'Initial Task 1')`,
        [taskId1, userA.id]
      );
      const schedId1 = getValidId();
      await pool.query(
        `INSERT INTO task_schedules (id, task_id, schedule_type, start_date, end_date) VALUES ($1, $2, 'ONCE', '2026-08-20', '2026-08-20')`,
        [schedId1, taskId1]
      );

      // 1. Check out Client A and start REPEATABLE READ transaction
      const clientA = await pool.connect();
      try {
        await clientA.query('BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');

        // Query tasks from Client A: sees Task 1
        const tasksQueryA = await clientA.query(
          `SELECT id, title FROM tasks WHERE user_id = $1 ORDER BY id ASC`,
          [userA.id]
        );
        assert.equal(tasksQueryA.rows.length, 1);
        assert.equal(tasksQueryA.rows[0].id, taskId1);

        // 2. Concurrently from outside transaction (Client B / pool), insert Task 2 and Schedule 2 and COMMIT
        const taskId2 = getValidId();
        await pool.query(
          `INSERT INTO tasks (id, user_id, title) VALUES ($1, $2, 'Concurrent Task 2')`,
          [taskId2, userA.id]
        );
        const schedId2 = getValidId();
        await pool.query(
          `INSERT INTO task_schedules (id, task_id, schedule_type, start_date, end_date) VALUES ($1, $2, 'ONCE', '2026-08-20', '2026-08-20')`,
          [schedId2, taskId2]
        );

        // 3. Query task_schedules from Client A (which began BEFORE Task 2 was committed)
        const schedulesQueryA = await clientA.query(
          `SELECT s.id, s.task_id FROM task_schedules s JOIN tasks t ON t.id = s.task_id WHERE t.user_id = $1 ORDER BY s.id ASC`,
          [userA.id]
        );

        // Because of REPEATABLE READ, Client A must NOT see Schedule 2!
        assert.equal(
          schedulesQueryA.rows.length,
          1,
          'REPEATABLE READ must isolate snapshot from concurrent insertions'
        );
        assert.equal(schedulesQueryA.rows[0].id, schedId1);

        // Commit Client A transaction
        await clientA.query('COMMIT');
      } catch (err) {
        await clientA.query('ROLLBACK').catch(() => {});
        throw err;
      } finally {
        clientA.release();
      }

      // Verify that a fresh repository snapshot NOW sees both tasks and both schedules
      const refreshRepo = new RefreshRepository();
      const freshSnapshot = await refreshRepo.getSnapshotData(userA.id);
      assert.equal(freshSnapshot.tasks.length, 2);
      assert.equal(freshSnapshot.schedules.length, 2);

      // Cleanup
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userA.id]);
    });
  });
});
