import { RefreshRepository } from './refresh.repository';
import {
  PlannerSnapshot,
  SnapshotTask,
  SnapshotSchedule,
  SnapshotCompletion,
} from './refresh.types';

export class RefreshService {
  constructor(private readonly refreshRepository: RefreshRepository = new RefreshRepository()) {}

  async getSnapshot(userId: string): Promise<PlannerSnapshot> {
    const rawData = await this.refreshRepository.getSnapshotData(userId);

    const tasks: SnapshotTask[] = rawData.tasks.map((row) => ({
      id: row.id,
      title: row.title,
      note: row.note ?? null,
      isImportant: row.is_important,
      createdAt:
        row.created_at instanceof Date
          ? row.created_at.toISOString()
          : new Date(row.created_at).toISOString(),
      updatedAt:
        row.updated_at instanceof Date
          ? row.updated_at.toISOString()
          : new Date(row.updated_at).toISOString(),
    }));

    const schedules: SnapshotSchedule[] = rawData.schedules.map((row) => ({
      id: row.id,
      taskId: row.task_id,
      scheduleType: row.schedule_type,
      startDate: row.start_date,
      endDate: row.end_date ?? null,
      scheduledTime: row.scheduled_time ?? null,
      intervalDays: row.interval_days ?? null,
      intervalAnchorDate: row.interval_anchor_date ?? null,
      weekdaysMask: row.weekdays_mask ?? null,
      reminderMinutesBefore: row.reminder_minutes_before ?? null,
      createdAt:
        row.created_at instanceof Date
          ? row.created_at.toISOString()
          : new Date(row.created_at).toISOString(),
      updatedAt:
        row.updated_at instanceof Date
          ? row.updated_at.toISOString()
          : new Date(row.updated_at).toISOString(),
    }));

    const completions: SnapshotCompletion[] = rawData.completions.map((row) => ({
      id: row.id,
      taskId: row.task_id,
      scheduleId: row.schedule_id ?? null,
      scheduledDate: row.scheduled_date ?? null,
      completedDate: row.completed_date,
      completedAt:
        row.completed_at instanceof Date
          ? row.completed_at.toISOString()
          : new Date(row.completed_at).toISOString(),
      titleSnapshot: row.title_snapshot ?? null,
      isImportantSnapshot: row.is_important_snapshot,
    }));

    return {
      tasks,
      schedules,
      completions,
    };
  }
}
