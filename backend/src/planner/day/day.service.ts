import { isScheduleOccurringOnDate, PlannerSchedule } from '../domain/recurrence';
import { DayRepository } from './day.repository';
import { PlannerDayResponse, TaskOccurrenceItem } from './day.types';

export class DayService {
  constructor(private readonly dayRepository: DayRepository = new DayRepository()) {}

  async getDay(userId: string, requestedDate: string): Promise<PlannerDayResponse> {
    const candidateRows = await this.dayRepository.findCandidateSchedulesForDate(
      userId,
      requestedDate
    );

    const timed: TaskOccurrenceItem[] = [];
    const anytime: TaskOccurrenceItem[] = [];
    const completed: TaskOccurrenceItem[] = [];

    for (const row of candidateRows) {
      const schedule: PlannerSchedule = {
        schedule_type: row.schedule_type,
        start_date: row.start_date,
        end_date: row.end_date,
        interval_days: row.interval_days,
        interval_anchor_date: row.interval_anchor_date,
        weekdays_mask: row.weekdays_mask,
      };

      if (!isScheduleOccurringOnDate(schedule, requestedDate)) {
        continue;
      }

      const completion = row.completion_id
        ? {
            id: row.completion_id,
            completedDate: row.completed_date!,
            completedAt:
              row.completed_at instanceof Date
                ? row.completed_at.toISOString()
                : new Date(row.completed_at!).toISOString(),
          }
        : null;

      const item: TaskOccurrenceItem = {
        taskId: row.task_id,
        scheduleId: row.schedule_id,
        title: row.title,
        note: row.note ?? null,
        isImportant: row.is_important,
        scheduleType: row.schedule_type,
        scheduledDate: requestedDate,
        scheduledTime: row.scheduled_time ?? null,
        reminderMinutesBefore: row.reminder_minutes_before ?? null,
        completion,
      };

      if (completion !== null) {
        completed.push(item);
      } else if (item.scheduledTime !== null) {
        timed.push(item);
      } else {
        anytime.push(item);
      }
    }

    // Sort incomplete timed items ascending by scheduledTime
    timed.sort((a, b) => {
      if (a.scheduledTime! < b.scheduledTime!) return -1;
      if (a.scheduledTime! > b.scheduledTime!) return 1;
      return 0;
    });

    return {
      date: requestedDate,
      timed,
      anytime,
      completed,
    };
  }
}
