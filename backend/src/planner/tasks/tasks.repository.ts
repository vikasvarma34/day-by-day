import { PoolClient } from 'pg';
import { getPool } from '../../db/pool';
import { ConflictError, NotFoundError } from '../../errors/http-errors';
import { CreateTaskDto, PlannerTaskResponse, EditTaskDto, EditTaskScheduleDto, CompleteTaskDto, UndoTaskDto, StopRecurrenceDto, TaskCompletionResponse, UpdateTaskResponse } from './tasks.types';
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

  async updateTask(userId: string, taskId: string, dto: EditTaskDto): Promise<UpdateTaskResponse> {
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

      let affectedCompletions: TaskCompletionResponse[] = [];

      if (dto.isImportant !== undefined) {
        await client.query(`UPDATE tasks SET is_important = $1 WHERE id = $2`, [dto.isImportant, taskId]);

        const lockedTitle = taskRes.rows[0].title;
        const compUpdateRes = await client.query(
          `UPDATE task_completions
           SET is_important_snapshot = $1,
               title_snapshot = COALESCE(title_snapshot, $3)
           WHERE task_id = $2
             AND (
               (schedule_id IS NULL AND scheduled_date IS NULL)
               OR EXISTS (
                 SELECT 1 FROM task_schedules s
                 WHERE s.id = task_completions.schedule_id
                   AND s.task_id = task_completions.task_id
                   AND s.schedule_type = 'ONCE'
               )
             )
           RETURNING id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot`,
          [dto.isImportant, taskId, lockedTitle]
        );

        affectedCompletions = compUpdateRes.rows.map((r: any) => ({
          id: r.id,
          taskId: r.task_id,
          scheduleId: r.schedule_id ?? null,
          scheduledDate: r.scheduled_date ?? null,
          completedDate: r.completed_date,
          completedAt: r.completed_at instanceof Date ? r.completed_at.toISOString() : new Date(r.completed_at).toISOString(),
          titleSnapshot: r.title_snapshot ?? null,
          isImportantSnapshot: r.is_important_snapshot,
        }));
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
      return { task: updatedTask!, completions: affectedCompletions };
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
      `SELECT id, title, note, is_important, created_at, updated_at
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
         interval_days, interval_anchor_date::text, weekdays_mask, reminder_minutes_before,
         created_at, updated_at
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
      createdAt: row.created_at instanceof Date ? row.created_at.toISOString() : new Date(row.created_at).toISOString(),
      updatedAt: row.updated_at instanceof Date ? row.updated_at.toISOString() : new Date(row.updated_at).toISOString(),
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
        createdAt: s.created_at instanceof Date ? s.created_at.toISOString() : new Date(s.created_at).toISOString(),
        updatedAt: s.updated_at instanceof Date ? s.updated_at.toISOString() : new Date(s.updated_at).toISOString(),
      })),
    };
  }

  async findTaskWithSchedules(taskId: string, userId: string): Promise<PlannerTaskResponse | null> {
    const pool = getPool();
    return this.findTaskWithSchedulesUsing(pool, taskId, userId);
  }

  async completeTask(userId: string, taskId: string, dto: CompleteTaskDto): Promise<TaskCompletionResponse> {
    const pool = getPool();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const taskRes = await client.query(
        `SELECT id, title, is_important FROM tasks WHERE id = $1 AND user_id = $2 FOR UPDATE`,
        [taskId, userId]
      );
      if (taskRes.rows.length === 0) {
        throw new NotFoundError('Task not found');
      }
      const taskRow = taskRes.rows[0];

      let completionRow: any;

      if (!dto.scheduleId) {
        const schedRes = await client.query(`SELECT 1 FROM task_schedules WHERE task_id = $1 LIMIT 1`, [taskId]);
        if (schedRes.rows.length > 0) {
          throw new ConflictError('Cannot complete a scheduled task as Later');
        }

        const compRes = await client.query(
          `SELECT id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot
           FROM task_completions WHERE task_id = $1 AND schedule_id IS NULL AND scheduled_date IS NULL LIMIT 1`,
          [taskId]
        );
        if (compRes.rows.length === 0) {
          const titleSnapshot = taskRow.title;
          const insertRes = await client.query(
            `INSERT INTO task_completions (
              task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot
            ) VALUES ($1, NULL, NULL, $2, NOW(), $3, $4)
            RETURNING id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot`,
            [taskId, dto.completedDate, titleSnapshot, taskRow.is_important]
          );
          completionRow = insertRes.rows[0];
        } else {
          completionRow = compRes.rows[0];
        }
      } else {
        const schedRes = await client.query(
          `SELECT id, schedule_type, start_date::text, end_date::text, interval_days, interval_anchor_date::text, weekdays_mask
           FROM task_schedules WHERE id = $1 AND task_id = $2 LIMIT 1`,
          [dto.scheduleId, taskId]
        );
        if (schedRes.rows.length === 0) {
          throw new ConflictError('Schedule not found for this task');
        }

        const scheduleRow = schedRes.rows[0];
        const schedule: PlannerSchedule = {
          schedule_type: scheduleRow.schedule_type,
          start_date: scheduleRow.start_date,
          end_date: scheduleRow.end_date,
          interval_days: scheduleRow.interval_days,
          interval_anchor_date: scheduleRow.interval_anchor_date,
          weekdays_mask: scheduleRow.weekdays_mask,
        };

        if (!isScheduleOccurringOnDate(schedule, dto.scheduledDate!)) {
          throw new ConflictError('Date is not a valid occurrence of this schedule');
        }

        const compRes = await client.query(
          `SELECT id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot
           FROM task_completions WHERE task_id = $1 AND schedule_id = $2 AND scheduled_date = $3 LIMIT 1`,
          [taskId, dto.scheduleId, dto.scheduledDate]
        );
        if (compRes.rows.length === 0) {
          const titleSnapshot = schedule.schedule_type === 'ONCE' ? taskRow.title : null;

          const insertRes = await client.query(
            `INSERT INTO task_completions (
              task_id, schedule_id, scheduled_date, completed_date, completed_at, title_snapshot, is_important_snapshot
            ) VALUES ($1, $2, $3, $4, NOW(), $5, $6)
            RETURNING id, task_id, schedule_id, scheduled_date::text, completed_date::text, completed_at, title_snapshot, is_important_snapshot`,
            [taskId, dto.scheduleId, dto.scheduledDate, dto.completedDate, titleSnapshot, taskRow.is_important]
          );
          completionRow = insertRes.rows[0];
        } else {
          completionRow = compRes.rows[0];
        }
      }

      await client.query('COMMIT');

      return {
        id: completionRow.id,
        taskId: completionRow.task_id,
        scheduleId: completionRow.schedule_id ?? null,
        scheduledDate: completionRow.scheduled_date ?? null,
        completedDate: completionRow.completed_date,
        completedAt: completionRow.completed_at instanceof Date ? completionRow.completed_at.toISOString() : new Date(completionRow.completed_at).toISOString(),
        titleSnapshot: completionRow.title_snapshot ?? null,
        isImportantSnapshot: completionRow.is_important_snapshot,
      };
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async undoTask(userId: string, taskId: string, dto: UndoTaskDto): Promise<void> {
    const pool = getPool();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const taskRes = await client.query(
        `SELECT id FROM tasks WHERE id = $1 AND user_id = $2 FOR UPDATE`,
        [taskId, userId]
      );
      if (taskRes.rows.length === 0) {
        throw new NotFoundError('Task not found');
      }

      if (!dto.scheduleId) {
        await client.query(
          `DELETE FROM task_completions WHERE task_id = $1 AND schedule_id IS NULL AND scheduled_date IS NULL`,
          [taskId]
        );
      } else {
        await client.query(
          `DELETE FROM task_completions WHERE task_id = $1 AND schedule_id = $2 AND scheduled_date = $3`,
          [taskId, dto.scheduleId, dto.scheduledDate]
        );
      }

      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async deleteTask(userId: string, taskId: string): Promise<void> {
    const pool = getPool();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      await client.query(`DELETE FROM tasks WHERE id = $1 AND user_id = $2`, [taskId, userId]);
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async stopRecurrence(userId: string, taskId: string, dto: StopRecurrenceDto): Promise<void> {
    const pool = getPool();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const taskRes = await client.query(
        `SELECT id FROM tasks WHERE id = $1 AND user_id = $2 FOR UPDATE`,
        [taskId, userId]
      );
      if (taskRes.rows.length === 0) {
        throw new NotFoundError('Task not found');
      }

      const schedRes = await client.query(
        `SELECT id, schedule_type, start_date::text, end_date::text, interval_days, interval_anchor_date::text, weekdays_mask
         FROM task_schedules
         WHERE task_id = $1
           AND start_date <= $2
           AND (end_date IS NULL OR end_date >= $2)
         ORDER BY start_date DESC
         LIMIT 1`,
        [taskId, dto.plannerToday]
      );
      if (schedRes.rows.length > 0) {
        const active = schedRes.rows[0];
        const newEndDate = dto.plannerToday;

        const dummySchedule: PlannerSchedule = {
          schedule_type: active.schedule_type,
          start_date: active.start_date,
          end_date: newEndDate,
          interval_days: active.interval_days,
          interval_anchor_date: active.interval_anchor_date,
          weekdays_mask: active.weekdays_mask,
        };

        let hasOccurrence = false;
        let current = active.start_date;
        let iterations = 0;
        while (current <= newEndDate && iterations < 730) {
          if (isScheduleOccurringOnDate(dummySchedule, current)) {
            hasOccurrence = true;
            break;
          }
          const d = new Date(current);
          d.setUTCDate(d.getUTCDate() + 1);
          current = d.toISOString().split('T')[0];
          iterations++;
        }

        const maxCompRes = await client.query(`SELECT MAX(scheduled_date)::text as max_date FROM task_completions WHERE schedule_id = $1`, [active.id]);
        if (maxCompRes.rows[0].max_date && maxCompRes.rows[0].max_date > newEndDate) {
          throw new ConflictError('Cannot stop recurrence because a future completion exists');
        }

        if (!hasOccurrence) {
          const compRes = await client.query(`SELECT 1 FROM task_completions WHERE schedule_id = $1 LIMIT 1`, [active.id]);
          if (compRes.rows.length === 0) {
             await client.query(`DELETE FROM task_schedules WHERE id = $1`, [active.id]);
          } else {
             await client.query(`UPDATE task_schedules SET end_date = $1 WHERE id = $2`, [newEndDate, active.id]);
          }
        } else {
          await client.query(`UPDATE task_schedules SET end_date = $1 WHERE id = $2`, [newEndDate, active.id]);
        }
      }

      const futureScheds = await client.query(`SELECT id FROM task_schedules WHERE task_id = $1 AND start_date > $2`, [taskId, dto.plannerToday]);
      for (const fs of futureScheds.rows) {
        const cRes = await client.query(`SELECT 1 FROM task_completions WHERE schedule_id = $1 LIMIT 1`, [fs.id]);
        if (cRes.rows.length === 0) {
          await client.query(`DELETE FROM task_schedules WHERE id = $1`, [fs.id]);
        } else {
          throw new ConflictError('Cannot stop recurrence because a future segment has recorded completions');
        }
      }

      const anySchedFinal = await client.query(`SELECT 1 FROM task_schedules WHERE task_id = $1 LIMIT 1`, [taskId]);
      const anyCompFinal = await client.query(`SELECT 1 FROM task_completions WHERE task_id = $1 LIMIT 1`, [taskId]);
      if (anySchedFinal.rows.length === 0 && anyCompFinal.rows.length === 0) {
         await client.query(`DELETE FROM tasks WHERE id = $1`, [taskId]);
      }

      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }
}
