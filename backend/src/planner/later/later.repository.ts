import { getPool } from '../../db/pool';
import { LaterTaskRow } from './later.types';

export class LaterRepository {
  /**
   * Queries all incomplete later tasks belonging to userId in deterministic creation order.
   * A later task has no task_schedules and no task_completions.
   */
  async findLaterTasks(userId: string): Promise<LaterTaskRow[]> {
    const pool = getPool();
    const result = await pool.query(
      `SELECT
         t.id,
         t.title,
         t.note,
         t.is_important
       FROM tasks t
       WHERE t.user_id = $1
         AND NOT EXISTS (
           SELECT 1
           FROM task_schedules s
           WHERE s.task_id = t.id
         )
         AND NOT EXISTS (
           SELECT 1
           FROM task_completions c
           WHERE c.task_id = t.id
         )
       ORDER BY t.created_at ASC, t.id ASC`,
      [userId]
    );

    return result.rows;
  }
}
