import { getPool } from '../../db/pool';
import { HistoryRow } from './history.types';

export class HistoryRepository {
  /**
   * Queries historical completions for a user before plannerToday.
   * Only includes:
   * - Important ONCE scheduled completions (s.schedule_type = 'ONCE')
   * - Important directly completed Later tasks (c.schedule_id IS NULL AND c.scheduled_date IS NULL)
   * Uses title_snapshot and is_important_snapshot.
   * Excludes recurring schedules and completions on or after plannerToday.
   * Supports optional case-insensitive substring search on title_snapshot.
   * Orders deterministically: completed_date DESC, completed_at DESC, completion id DESC.
   */
  async findHistoryCompletions(
    userId: string,
    plannerToday: string,
    search?: string
  ): Promise<HistoryRow[]> {
    const pool = getPool();
    const result = await pool.query(
      `SELECT
         c.id AS completion_id,
         c.task_id,
         c.title_snapshot AS title,
         c.completed_date::text AS completed_date,
         c.completed_at
       FROM task_completions c
       JOIN tasks t ON t.id = c.task_id
       LEFT JOIN task_schedules s ON s.id = c.schedule_id AND s.task_id = c.task_id
       WHERE t.user_id = $1
         AND c.completed_date < $2::date
         AND c.is_important_snapshot = true
         AND c.title_snapshot IS NOT NULL
         AND (
           (c.schedule_id IS NULL AND c.scheduled_date IS NULL)
           OR (c.schedule_id IS NOT NULL AND c.scheduled_date IS NOT NULL AND s.schedule_type = 'ONCE')
         )
         AND ($3::text IS NULL OR c.title_snapshot ILIKE '%' || $3 || '%')
       ORDER BY c.completed_date DESC, c.completed_at DESC, c.id DESC`,
      [userId, plannerToday, search ?? null]
    );

    return result.rows;
  }
}
