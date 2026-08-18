import { ScheduleType } from './recurrence';

export interface TaskOccurrenceCompletion {
  id: string;
  completedDate: string;
  completedAt: string;
}

export interface TaskOccurrenceItem {
  taskId: string;
  scheduleId: string;
  title: string;
  note: string | null;
  isImportant: boolean;
  scheduleType: ScheduleType;
  scheduledDate: string;
  scheduledTime: string | null;
  reminderMinutesBefore: number | null;
  completion: TaskOccurrenceCompletion | null;
}

export interface PlannerDayResponse {
  date: string;
  timed: TaskOccurrenceItem[];
  anytime: TaskOccurrenceItem[];
  completed: TaskOccurrenceItem[];
}

export interface CandidateScheduleRow {
  task_id: string;
  title: string;
  note: string | null;
  is_important: boolean;
  schedule_id: string;
  schedule_type: ScheduleType;
  start_date: string;
  end_date: string | null;
  scheduled_time: string | null;
  interval_days: number | null;
  interval_anchor_date: string | null;
  weekdays_mask: number | null;
  reminder_minutes_before: number | null;
  completion_id: string | null;
  completed_date: string | null;
  completed_at: Date | string | null;
}
