import { PoolClient } from 'pg';
import { getPool } from '../../db/pool';
import { ConflictError } from '../../errors/http-errors';
import { CreateTaskDto, PlannerTaskResponse } from './tasks.types';

type QueryExecutor = {
  query: PoolClient['query'];
};

export class TasksRepository {
  /**
   * Idempotently creates a task and optionally its initial schedule in an atomic transaction.
   * If the UUID already exists:
   *  - if it belongs to another user, throws ConflictError.
   *  - if it belongs to the same user, does not overwrite and returns { isNew: false, task: existingTask }.
   * Returns { isNew: true, task: newlyCreatedTask } on successful new creation.
   *
   * Executes entirely on a single checked-out PoolClient to prevent pool starvation.
   */
  async createIdempotent(userId: string, dto: CreateTaskDto): Promise<{ isNew: boolean; task: PlannerTaskResponse }> {
    const pool = getPool();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const insertTaskRes = await client.query(
        `INSERT INTO tasks (id, user_id, title, note, is_important)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (id) DO NOTHING
         RETURNING id`,
        [dto.id, userId, dto.title, dto.note ?? null, dto.isImportant ?? false]
      );

      if (insertTaskRes.rows.length === 0) {
        const existingRes = await client.query(`SELECT user_id FROM tasks WHERE id = $1`, [dto.id]);
        if (existingRes.rows.length === 0 || existingRes.rows[0].user_id !== userId) {
          await client.query('ROLLBACK');
          throw new ConflictError('Task ID conflict');
        }

        const existingTask = await this.findTaskWithSchedulesUsing(client, dto.id, userId);
        if (!existingTask) {
          await client.query('ROLLBACK');
          throw new ConflictError('Task not found after rollback');
        }

        await client.query('COMMIT');
        return { isNew: false, task: existingTask };
      }

      if (dto.schedule) {
        await client.query(
          `INSERT INTO task_schedules (
             task_id, schedule_type, start_date, end_date, interval_days, 
             interval_anchor_date, weekdays_mask, scheduled_time, reminder_minutes_before
           )
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
          [
            dto.id,
            dto.schedule.type,
            dto.schedule.startDate,
            dto.schedule.endDate ?? null,
            dto.schedule.intervalDays ?? null,
            dto.schedule.intervalAnchorDate ?? null,
            dto.schedule.weekdaysMask ?? null,
            dto.schedule.scheduledTime ?? null,
            dto.schedule.reminderMinutesBefore ?? null,
          ]
        );
      }

      const newTask = await this.findTaskWithSchedulesUsing(client, dto.id, userId);
      await client.query('COMMIT');

      return { isNew: true, task: newTask! };
    } catch (err) {
      try {
        await client.query('ROLLBACK');
      } catch {
        // ignore rollback errors if connection was aborted/closed
      }
      throw err;
    } finally {
      client.release();
    }
  }

  private async findTaskWithSchedulesUsing(
    executor: QueryExecutor,
    taskId: string,
    userId: string
  ): Promise<PlannerTaskResponse | null> {
    const taskRes = await executor.query(
      `SELECT id, title, note, is_important
       FROM tasks
       WHERE id = $1 AND user_id = $2`,
      [taskId, userId]
    );

    if (taskRes.rows.length === 0) {
      return null;
    }

    const row = taskRes.rows[0];

    const schedulesRes = await executor.query(
      `SELECT 
         id, schedule_type, start_date::text, end_date::text, scheduled_time,
         interval_days, interval_anchor_date::text, weekdays_mask, reminder_minutes_before
       FROM task_schedules
       WHERE task_id = $1
       ORDER BY start_date ASC, id ASC`,
      [taskId]
    );

    return {
      id: row.id,
      title: row.title,
      note: row.note ?? null,
      isImportant: row.is_important,
      schedules: schedulesRes.rows.map((s: any) => ({
        id: s.id,
        type: s.schedule_type,
        startDate: s.start_date,
        endDate: s.end_date ?? null,
        scheduledTime: s.scheduled_time ?? null,
        intervalDays: s.interval_days ?? null,
        intervalAnchorDate: s.interval_anchor_date ?? null,
        weekdaysMask: s.weekdays_mask ?? null,
        reminderMinutesBefore: s.reminder_minutes_before ?? null,
      })),
    };
  }

  async findTaskWithSchedules(taskId: string, userId: string): Promise<PlannerTaskResponse | null> {
    const pool = getPool();
    return this.findTaskWithSchedulesUsing(pool, taskId, userId);
  }
}
