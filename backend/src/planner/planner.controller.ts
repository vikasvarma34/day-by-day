import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth/auth.middleware';
import { UnauthorizedError } from '../errors/http-errors';
import { validatePlannerDate } from './date-validation';
import { isScheduleOccurringOnDate, PlannerSchedule } from './recurrence';
import { PlannerRepository } from './planner.repository';
import { PlannerDayResponse, TaskOccurrenceItem } from './types';

export class PlannerController {
  constructor(private readonly plannerRepository: PlannerRepository = new PlannerRepository()) {}

  getDay = async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const requestedDate = validatePlannerDate(req.params.date, 'date');
      const candidateRows = await this.plannerRepository.findCandidateSchedulesForDate(
        req.user.id,
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

      const response: PlannerDayResponse = {
        date: requestedDate,
        timed,
        anytime,
        completed,
      };

      res.status(200).json(response);
    } catch (err) {
      next(err);
    }
  };
}
