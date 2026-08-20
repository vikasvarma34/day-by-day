import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getPool, closePool } from '../../src/db/pool';
import { createUserAccount } from '../../src/auth/user-creation';
import { deleteTestUserById, generateAutomatedTestEmail } from './auth-test-fixtures';

test('Planner Schema Integration Suite', async (t) => {
  const pool = getPool();
  let userId: string;
  let taskId: string;

  t.before(async () => {
    const email = generateAutomatedTestEmail('planner.schema');
    const user = await createUserAccount({
      email,
      password: 'StrongPassword12345!',
      firstName: 'Planner',
      lastName: 'Tester'
    });
    userId = user.id;

    // Create a valid task to be used across schedule tests
    const res = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Global Test Task') RETURNING id`,
      [userId]
    );
    taskId = res.rows[0].id;
  });

  t.after(async () => {
    if (userId) {
      await pool.query('DELETE FROM tasks WHERE user_id = $1', [userId]);
      await deleteTestUserById(userId);
    }
    await closePool();
  });

  async function expectDbError(queryPromise: Promise<any>, snippet: string) {
    try {
      await queryPromise;
      assert.fail(`Expected query to fail with '${snippet}'`);
    } catch (err: any) {
      if (err.name === 'AssertionError') throw err;
      const msg = err.message || '';
      const cons = err.constraint || '';
      assert.ok(
        msg.includes(snippet) || cons.includes(snippet),
        `Expected DB error containing '${snippet}', but got message: '${msg}', constraint: '${cons}'`
      );
    }
  }

  await t.test('tasks: rejects blank title', async () => {
    await expectDbError(
      pool.query(`INSERT INTO tasks (user_id, title) VALUES ($1, '   ')`, [userId]),
      'tasks_title_check'
    );
  });

  await t.test('tasks: rejects note longer than 500 characters', async () => {
    const longNote = 'A'.repeat(501);
    await expectDbError(
      pool.query(`INSERT INTO tasks (user_id, title, note) VALUES ($1, 'Title', $2)`, [userId, longNote]),
      'tasks_note_check'
    );
  });

  await t.test('task_schedules: rejects invalid schedule type', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
         VALUES ($1, 'YEARLY', '2026-01-01', '2026-01-01')`,
        [taskId]
      ),
      'task_schedules_schedule_type_check'
    );
  });

  await t.test('task_schedules: rejects ONCE schedule carrying interval/weekday fields', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, interval_days) 
         VALUES ($1, 'ONCE', '2026-01-01', '2026-01-01', 2)`,
        [taskId]
      ),
      'task_schedules_once_check'
    );
  });

  await t.test('task_schedules: rejects ONCE where start/end dates differ', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
         VALUES ($1, 'ONCE', '2026-01-01', '2026-01-02')`,
        [taskId]
      ),
      'task_schedules_once_check'
    );
  });

  await t.test('task_schedules: rejects INTERVAL_DAYS without a valid interval/anchor', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_days) 
         VALUES ($1, 'INTERVAL_DAYS', '2026-01-01', 1)`,
        [taskId] // missing anchor date
      ),
      'task_schedules_interval_check'
    );
  });

  await t.test('task_schedules: rejects INTERVAL_DAYS with a NULL interval_days', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_anchor_date) 
         VALUES ($1, 'INTERVAL_DAYS', '2026-01-01', '2026-01-01')`,
        [taskId] // missing interval_days (implicitly NULL)
      ),
      'task_schedules_interval_check'
    );
  });

  await t.test('task_schedules: rejects invalid weekday mask', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, weekdays_mask) 
         VALUES ($1, 'WEEKDAYS', '2026-01-01', 128)`,
        [taskId] // mask > 127
      ),
      'task_schedules_weekdays_check'
    );
  });

  await t.test('task_schedules: rejects WEEKDAYS with a NULL weekdays_mask', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date) 
         VALUES ($1, 'WEEKDAYS', '2026-01-01')`,
        [taskId] // missing weekdays_mask (implicitly NULL)
      ),
      'task_schedules_weekdays_check'
    );
  });

  await t.test('task_schedules: rejects end date before start date', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
         VALUES ($1, 'ONCE', '2026-01-02', '2026-01-01')`,
        [taskId]
      ),
      'task_schedules_end_date_check'
    );
  });

  await t.test('task_schedules: rejects invalid reminder value', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, scheduled_time, reminder_minutes_before) 
         VALUES ($1, 'ONCE', '2026-01-01', '2026-01-01', '10:00:00', 45)`,
        [taskId]
      ),
      'task_schedules_reminder_check'
    );
  });

  await t.test('task_schedules: rejects reminder without scheduled time', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, reminder_minutes_before) 
         VALUES ($1, 'ONCE', '2026-01-01', '2026-01-01', 15)`,
        [taskId]
      ),
      'task_schedules_reminder_time_check'
    );
  });

  await t.test('task_schedules: rejects two open-ended schedule rows for the same task', async () => {
    await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, weekdays_mask) 
       VALUES ($1, 'WEEKDAYS', '2026-01-01', 31)`,
      [taskId]
    );

    await expectDbError(
      pool.query(
        `INSERT INTO task_schedules (task_id, schedule_type, start_date, interval_days, interval_anchor_date) 
         VALUES ($1, 'INTERVAL_DAYS', '2026-02-01', 2, '2026-02-01')`,
        [taskId]
      ),
      'task_schedules_single_open_ended_idx'
    );
  });

  await t.test('task_completions: rejects duplicate completion for the same task/date', async () => {
    const res = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
       VALUES ($1, 'ONCE', '2026-03-01', '2026-03-01') RETURNING id`,
      [taskId]
    );
    const scheduleId = res.rows[0].id;

    await pool.query(
      `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, $2, '2026-03-01', '2026-03-01', NOW(), false)`,
      [taskId, scheduleId]
    );

    await expectDbError(
      pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
         VALUES ($1, $2, '2026-03-01', '2026-03-01', NOW(), false)`,
        [taskId, scheduleId]
      ),
      'task_completions_unique_occurrence'
    );
  });

  await t.test('task_completions: rejects more than one direct-Later completion for one task', async () => {
    await pool.query(
      `INSERT INTO task_completions (task_id, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, '2026-03-01', NOW(), false)`,
      [taskId]
    );

    await expectDbError(
      pool.query(
        `INSERT INTO task_completions (task_id, completed_date, completed_at, is_important_snapshot)
         VALUES ($1, '2026-03-02', NOW(), false)`,
        [taskId]
      ),
      'task_completions_unique_occurrence'
    );
  });

  await t.test('task_completions: rejects completion where only one of schedule_id/scheduled_date is present', async () => {
    await expectDbError(
      pool.query(
        `INSERT INTO task_completions (task_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
         VALUES ($1, '2026-03-01', '2026-03-01', NOW(), false)`,
        [taskId] // missing schedule_id
      ),
      'task_completions_schedule_sync_check'
    );
  });

  await t.test('task_completions: rejects completion referencing a schedule belonging to a different task', async () => {
    // Create another task
    const tRes = await pool.query(
      `INSERT INTO tasks (user_id, title) VALUES ($1, 'Another Task') RETURNING id`,
      [userId]
    );
    const otherTaskId = tRes.rows[0].id;

    // Create schedule on another task
    const sRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
       VALUES ($1, 'ONCE', '2026-04-01', '2026-04-01') RETURNING id`,
      [otherTaskId]
    );
    const otherScheduleId = sRes.rows[0].id;

    // Try to complete it using the first task's ID
    await expectDbError(
      pool.query(
        `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
         VALUES ($1, $2, '2026-04-01', '2026-04-01', NOW(), false)`,
        [taskId, otherScheduleId]
      ),
      'task_completions_schedule_fk'
    );
  });

  await t.test('task_completions: deleting a referenced schedule must not silently cascade-delete its completion', async () => {
    // We already have a schedule and a scheduled completion from a previous test
    // Let's create a fresh one to be sure
    const sRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
       VALUES ($1, 'ONCE', '2026-05-01', '2026-05-01') RETURNING id`,
      [taskId]
    );
    const scheduleId = sRes.rows[0].id;

    await pool.query(
      `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, $2, '2026-05-01', '2026-05-01', NOW(), false)`,
      [taskId, scheduleId]
    );

    await expectDbError(
      pool.query(`DELETE FROM task_schedules WHERE id = $1`, [scheduleId]),
      'task_completions_schedule_fk'
    );
  });

  await t.test('proves valid ONCE, INTERVAL_DAYS, WEEKDAYS, direct-Later, and scheduled completions succeed', async () => {
    // ONCE
    const onceRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date) 
       VALUES ($1, 'ONCE', '2026-10-01', '2026-10-01') RETURNING id`,
      [taskId]
    );
    assert.ok(onceRes.rows[0].id);

    // INTERVAL_DAYS
    const intervalRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, interval_days, interval_anchor_date) 
       VALUES ($1, 'INTERVAL_DAYS', '2026-11-01', '2026-12-01', 7, '2026-11-01') RETURNING id`,
      [taskId]
    );
    assert.ok(intervalRes.rows[0].id);

    // WEEKDAYS
    // Notice: we can't insert another open-ended schedule due to 'task_schedules_single_open_ended_idx' since we inserted one in an earlier test.
    // We can provide an end_date.
    const weekdaysRes = await pool.query(
      `INSERT INTO task_schedules (task_id, schedule_type, start_date, end_date, weekdays_mask) 
       VALUES ($1, 'WEEKDAYS', '2026-12-01', '2026-12-31', 62) RETURNING id`,
      [taskId]
    );
    assert.ok(weekdaysRes.rows[0].id);

    // direct-Later completion: We already inserted one earlier. We can't insert a second one (UNIQUE NULLS NOT DISTINCT).
    // Let's create a new task to prove direct-Later completion succeeds cleanly.
    const tRes = await pool.query(`INSERT INTO tasks (user_id, title) VALUES ($1, 'Later Task') RETURNING id`, [userId]);
    const laterTaskId = tRes.rows[0].id;
    const laterCompRes = await pool.query(
      `INSERT INTO task_completions (task_id, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, '2026-06-01', NOW(), false) RETURNING id`,
      [laterTaskId]
    );
    assert.ok(laterCompRes.rows[0].id);

    // Scheduled completion
    const schedCompRes = await pool.query(
      `INSERT INTO task_completions (task_id, schedule_id, scheduled_date, completed_date, completed_at, is_important_snapshot)
       VALUES ($1, $2, '2026-11-01', '2026-11-01', NOW(), false) RETURNING id`,
      [taskId, intervalRes.rows[0].id]
    );
    assert.ok(schedCompRes.rows[0].id);
  });
});
