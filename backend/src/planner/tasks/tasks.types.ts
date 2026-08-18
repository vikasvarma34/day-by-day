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
}

export interface PlannerTaskResponse {
  id: string;
  title: string;
  note: string | null;
  isImportant: boolean;
  schedules: PlannerTaskScheduleResponse[];
}
