export interface SnapshotTask {
  id: string;
  title: string;
  note: string | null;
  isImportant: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SnapshotSchedule {
  id: string;
  taskId: string;
  scheduleType: string;
  startDate: string;
  endDate: string | null;
  scheduledTime: string | null;
  intervalDays: number | null;
  intervalAnchorDate: string | null;
  weekdaysMask: number | null;
  reminderMinutesBefore: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SnapshotCompletion {
  id: string;
  taskId: string;
  scheduleId: string | null;
  scheduledDate: string | null;
  completedDate: string;
  completedAt: string;
  titleSnapshot: string | null;
  isImportantSnapshot: boolean;
}

export interface PlannerSnapshot {
  tasks: SnapshotTask[];
  schedules: SnapshotSchedule[];
  completions: SnapshotCompletion[];
}

export interface SnapshotTaskRow {
  id: string;
  title: string;
  note: string | null;
  is_important: boolean;
  created_at: Date | string;
  updated_at: Date | string;
}

export interface SnapshotScheduleRow {
  id: string;
  task_id: string;
  schedule_type: string;
  start_date: string;
  end_date: string | null;
  scheduled_time: string | null;
  interval_days: number | null;
  interval_anchor_date: string | null;
  weekdays_mask: number | null;
  reminder_minutes_before: number | null;
  created_at: Date | string;
  updated_at: Date | string;
}

export interface SnapshotCompletionRow {
  id: string;
  task_id: string;
  schedule_id: string | null;
  scheduled_date: string | null;
  completed_date: string;
  completed_at: Date | string;
  title_snapshot: string | null;
  is_important_snapshot: boolean;
}

export interface RawSnapshotRows {
  tasks: SnapshotTaskRow[];
  schedules: SnapshotScheduleRow[];
  completions: SnapshotCompletionRow[];
}
