export interface CreateTaskScheduleDto {
  type: 'ONCE' | 'INTERVAL_DAYS' | 'WEEKDAYS';
  startDate: string; // YYYY-MM-DD
  endDate?: string | null;
  intervalDays?: number | null;
  intervalAnchorDate?: string | null;
  weekdaysMask?: number | null;
  scheduledTime?: string | null; // HH:mm:ss
  reminderMinutesBefore?: number | null;
}

export interface CreateTaskDto {
  id: string; // UUID v4
  title: string;
  note?: string | null;
  isImportant?: boolean;
  schedule?: CreateTaskScheduleDto | null;
}

export interface PlannerTaskScheduleResponse {
  id: string;
  type: string;
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

export interface PlannerTaskResponse {
  id: string;
  title: string;
  note: string | null;
  isImportant: boolean;
  createdAt: string;
  updatedAt: string;
  schedules: PlannerTaskScheduleResponse[];
}

export interface EditTaskScheduleDto {
  type: 'ONCE' | 'INTERVAL_DAYS' | 'WEEKDAYS';
  startDate: string; // YYYY-MM-DD
  endDate?: string | null;
  intervalDays?: number | null;
  intervalAnchorDate?: string | null;
  weekdaysMask?: number | null;
  scheduledTime?: string | null; // HH:mm:ss
  reminderMinutesBefore?: number | null;
}

export interface EditTaskDto {
  plannerToday: string;
  effectiveDate: string;
  title?: string;
  note?: string | null;
  isImportant?: boolean;
  schedule?: EditTaskScheduleDto | null;
}

export interface CompleteTaskDto {
  plannerToday: string;
  completedDate: string;
  scheduleId?: string;
  scheduledDate?: string;
}

export interface UndoTaskDto {
  scheduleId?: string;
  scheduledDate?: string;
}

export interface StopRecurrenceDto {
  plannerToday: string;
}

export interface TaskCompletionResponse {
  id: string;
  taskId: string;
  scheduleId: string | null;
  scheduledDate: string | null;
  completedDate: string;
  completedAt: string;
  titleSnapshot: string | null;
  isImportantSnapshot: boolean;
}

export interface UpdateTaskResponse {
  task: PlannerTaskResponse;
  completions: TaskCompletionResponse[];
}
