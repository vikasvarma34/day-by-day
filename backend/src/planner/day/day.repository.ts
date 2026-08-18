import { getPool } from '../../db/pool';
import { CandidateScheduleRow } from './day.types';

export class DayRepository {
  /**
   * Queries all candidate schedule segments belonging to tasks owned by userId
   * where start_date <= requestedDate and (end_date IS NULL or end_date >= requestedDate).
   * Left joins task_completions matching the exact schedule, task, and requested date.
   * Dates and times are explicitly cast to text to prevent timezone distortion.
   */
  async findCandidateSchedulesForDate(
    userId: string,
    requestedDate: string
  ): Promise<CandidateScheduleRow[]> {
    const pool = getPool();
    const result = await pool.query(
      `SELECT
         t.id AS task_id,
         t.title,
         t.note,
         t.is_important,
         s.id AS schedule_id,
         s.schedule_type,
         s.start_date::text AS start_date,
         s.end_date::text AS end_date,
         s.scheduled_time::text AS scheduled_time,
         s.interval_days,
         s.interval_anchor_date::text AS interval_anchor_date,
         s.weekdays_mask,
         s.reminder_minutes_before,
         c.id AS completion_id,
         c.completed_date::text AS completed_date,
         c.completed_at
       FROM task_schedules s
       JOIN tasks t ON s.task_id = t.id
       LEFT JOIN task_completions c ON (
         c.task_id = s.task_id
         AND c.schedule_id = s.id
         AND c.scheduled_date = $2::date
       )
       WHERE t.user_id = $1
         AND s.start_date <= $2::date
         AND (s.end_date IS NULL OR s.end_date >= $2::date)`,
      [userId, requestedDate]
    );

    return result.rows;
  }
}
