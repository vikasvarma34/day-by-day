import { getPool } from '../../db/pool';
import { RawSnapshotRows, SnapshotTaskRow, SnapshotScheduleRow, SnapshotCompletionRow } from './refresh.types';

export class RefreshRepository {
  /**
   * Collects an authoritative, point-in-time Planner snapshot for a user.
   * Uses a single PostgreSQL client and a REPEATABLE READ READ ONLY transaction
   * to guarantee that tasks, schedules, and completions represent one coherent snapshot.
   * Transaction commits and releases the client immediately after queries complete.
   */
  async getSnapshotData(userId: string): Promise<RawSnapshotRows> {
    const pool = getPool();
    const client = await pool.connect();

    try {
      await client.query('BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');

      const tasksResult = await client.query<SnapshotTaskRow>(
        `SELECT
           t.id,
           t.title,
           t.note,
           t.is_important,
           t.created_at,
           t.updated_at
         FROM tasks t
         WHERE t.user_id = $1
         ORDER BY t.created_at ASC, t.id ASC`,
        [userId]
      );

      const schedulesResult = await client.query<SnapshotScheduleRow>(
        `SELECT
           s.id,
           s.task_id,
           s.schedule_type,
           s.start_date::text AS start_date,
           s.end_date::text AS end_date,
           s.scheduled_time::text AS scheduled_time,
           s.interval_days,
           s.interval_anchor_date::text AS interval_anchor_date,
           s.weekdays_mask,
           s.reminder_minutes_before,
           s.created_at,
           s.updated_at
         FROM task_schedules s
         JOIN tasks t ON t.id = s.task_id
         WHERE t.user_id = $1
         ORDER BY s.created_at ASC, s.id ASC`,
        [userId]
      );

      const completionsResult = await client.query<SnapshotCompletionRow>(
        `SELECT
           c.id,
           c.task_id,
           c.schedule_id,
           c.scheduled_date::text AS scheduled_date,
           c.completed_date::text AS completed_date,
           c.completed_at,
           c.title_snapshot,
           c.is_important_snapshot
         FROM task_completions c
         JOIN tasks t ON t.id = c.task_id
         WHERE t.user_id = $1
         ORDER BY c.completed_date ASC, c.completed_at ASC, c.id ASC`,
        [userId]
      );

      await client.query('COMMIT');

      return {
        tasks: tasksResult.rows,
        schedules: schedulesResult.rows,
        completions: completionsResult.rows,
      };
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      throw err;
    } finally {
      client.release();
    }
  }
}
