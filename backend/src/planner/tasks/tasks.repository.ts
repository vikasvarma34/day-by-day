import { PoolClient } from 'pg';
import { getPool } from '../../db/pool';
import { ConflictError, NotFoundError } from '../../errors/http-errors';
import { CreateTaskDto, PlannerTaskResponse, EditTaskDto, EditTaskScheduleDto } from './tasks.types';
import { PlannerSchedule, isScheduleOccurringOnDate } from '../domain/recurrence';

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

  async updateTask(userId: string, taskId: string, dto: EditTaskDto): Promise<PlannerTaskResponse> {
    const pool = getPool();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const taskRes = await client.query(
        `SELECT id, title, note, is_important FROM tasks WHERE id = $1 AND user_id = $2 FOR UPDATE`,
        [taskId, userId]
      );
      if (taskRes.rows.length === 0) {
        await client.query('ROLLBACK');
        throw new NotFoundError('Task not found');
      }

      if (dto.title !== undefined) {
        await client.query(`UPDATE tasks SET title = $1 WHERE id = $2`, [dto.title, taskId]);
      }
      if (dto.note !== undefined) {
        await client.query(`UPDATE tasks SET note = $1 WHERE id = $2`, [dto.note, taskId]);
      }
      if (dto.isImportant !== undefined) {
        await client.query(`UPDATE tasks SET is_important = $1 WHERE id = $2`, [dto.isImportant, taskId]);
      }

      if (dto.schedule !== undefined && dto.schedule !== null) {
        const s = dto.schedule;
        const effectiveDate = dto.effectiveDate;

        const schedRes = await client.query(
          `SELECT id, schedule_type, start_date::text, end_date::text, interval_days, interval_anchor_date::text, weekdays_mask
           FROM task_schedules WHERE task_id = $1 ORDER BY start_date ASC`,
          [taskId]
        );
        const existingSchedules = schedRes.rows;

        if (existingSchedules.length === 0) {
          const compRes = await client.query(
            `SELECT 1 FROM task_completions WHERE task_id = $1 AND schedule_id IS NULL AND scheduled_date IS NULL LIMIT 1`,
            [taskId]
          );
          if (compRes.rows.length > 0) {
            throw new ConflictError('Cannot schedule a completed Later task. Undo completion first.');
          }
          await this.insertSchedule(client, taskId, s);
        } else if (existingSchedules.length === 1 && existingSchedules[0].schedule_type === 'ONCE') {
          const target = existingSchedules[0];
          const compRes = await client.query(`SELECT 1 FROM task_completions WHERE schedule_id = $1 LIMIT 1`, [target.id]);
          if (compRes.rows.length > 0) {
            throw new ConflictError('Cannot reschedule a completed ONCE occurrence. Undo first.');
          }
          await client.query(
            `UPDATE task_schedules
             SET schedule_type = $1, start_date = $2, end_date = $3, interval_days = $4,
                 interval_anchor_date = $5, weekdays_mask = $6, scheduled_time = $7, reminder_minutes_before = $8
             WHERE id = $9`,
            [
              s.type, s.startDate, s.endDate ?? null, s.intervalDays ?? null,
              s.intervalAnchorDate ?? null, s.weekdaysMask ?? null, s.scheduledTime ?? null,
              s.reminderMinutesBefore ?? null, target.id
            ]
          );
        } else {
          const activeBeforeD = existingSchedules.find(sc => sc.start_date < effectiveDate && (sc.end_date === null || sc.end_date >= effectiveDate));

          // Interval anchor logic: cadence-preserving edit keeps anchor.
          let newAnchorDate = s.type === 'INTERVAL_DAYS' ? s.startDate : null;
          if (activeBeforeD && activeBeforeD.schedule_type === 'INTERVAL_DAYS' && s.type === 'INTERVAL_DAYS' && activeBeforeD.interval_days === s.intervalDays) {
            newAnchorDate = activeBeforeD.interval_anchor_date;
          }
          s.intervalAnchorDate = newAnchorDate;

          if (activeBeforeD) {
            // Must check for conflicting completions on this segment BEFORE modifying it
            const compRes = await client.query(`SELECT 1 FROM task_completions WHERE schedule_id = $1 AND scheduled_date >= $2 LIMIT 1`, [activeBeforeD.id, effectiveDate]);
            if (compRes.rows.length > 0) {
              throw new ConflictError('Cannot modify schedule because future completions exist. Undo them first.');
            }

            const newEndDate = this.subtractOneDay(effectiveDate);

            // Check finite preserved segment validity
            const dummySchedule: PlannerSchedule = {
              schedule_type: activeBeforeD.schedule_type,
              start_date: activeBeforeD.start_date,
              end_date: newEndDate,
              interval_days: activeBeforeD.interval_days,
              interval_anchor_date: activeBeforeD.interval_anchor_date,
              weekdays_mask: activeBeforeD.weekdays_mask,
            };

            let hasOccurrence = false;
            let current = dummySchedule.start_date;
            let iterations = 0;
            // evaluate up to 7 days to see if the segment contains any occurrence
            while (current <= newEndDate && iterations < 7) {
              if (isScheduleOccurringOnDate(dummySchedule, current)) {
                hasOccurrence = true;
                break;
              }
              const d = new Date(current);
              d.setUTCDate(d.getUTCDate() + 1);
              current = d.toISOString().split('T')[0];
              iterations++;
            }

            if (!hasOccurrence) {
              const refRes = await client.query(`SELECT 1 FROM task_completions WHERE schedule_id = $1 LIMIT 1`, [activeBeforeD.id]);
              if (refRes.rows.length === 0) {
                await client.query(`DELETE FROM task_schedules WHERE id = $1`, [activeBeforeD.id]);
              } else {
                await client.query(`UPDATE task_schedules SET end_date = $1 WHERE id = $2`, [newEndDate, activeBeforeD.id]);
              }
            } else {
              await client.query(`UPDATE task_schedules SET end_date = $1 WHERE id = $2`, [newEndDate, activeBeforeD.id]);
            }
          }

          const futureSchedules = existingSchedules.filter(sc => sc.start_date >= effectiveDate);
          for (const fs of futureSchedules) {
            const compRes = await client.query(`SELECT 1 FROM task_completions WHERE schedule_id = $1 AND scheduled_date >= $2 LIMIT 1`, [fs.id, effectiveDate]);
            if (compRes.rows.length > 0) {
              throw new ConflictError('Cannot modify schedule because future completions exist. Undo them first.');
            }
            await client.query(`DELETE FROM task_schedules WHERE id = $1`, [fs.id]);
          }

          await this.insertSchedule(client, taskId, s);
        }
      }

      const updatedTask = await this.findTaskWithSchedulesUsing(client, taskId, userId);
      await client.query('COMMIT');
      return updatedTask!;
    } catch (err) {
      try {
        await client.query('ROLLBACK');
      } catch {}
      throw err;
    } finally {
      client.release();
    }
  }

  private subtractOneDay(dateStr: string): string {
    const d = new Date(dateStr);
    d.setUTCDate(d.getUTCDate() - 1);
    return d.toISOString().split('T')[0];
  }

  private async insertSchedule(client: PoolClient, taskId: string, s: EditTaskScheduleDto) {
    await client.query(
      `INSERT INTO task_schedules (
         task_id, schedule_type, start_date, end_date, interval_days,
         interval_anchor_date, weekdays_mask, scheduled_time, reminder_minutes_before
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
      [
        taskId, s.type, s.startDate, s.endDate ?? null, s.intervalDays ?? null,
        s.intervalAnchorDate ?? null, s.weekdaysMask ?? null, s.scheduledTime ?? null,
        s.reminderMinutesBefore ?? null,
      ]
    );
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
