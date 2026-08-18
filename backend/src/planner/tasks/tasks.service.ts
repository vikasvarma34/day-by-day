import { TasksRepository } from './tasks.repository';
import { CreateTaskDto, PlannerTaskResponse } from './tasks.types';
import { validatePlannerDate } from '../domain/date-validation';
import { isScheduleOccurringOnDate, PlannerSchedule } from '../domain/recurrence';
import { BadRequestError } from '../../errors/http-errors';

const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/;
const ALLOWED_REMINDERS = [0, 5, 10, 15, 30, 60, 1440, 2880];

export class TasksService {
  constructor(private readonly tasksRepository: TasksRepository = new TasksRepository()) {}

  async createTask(
    userId: string,
    payload: any
  ): Promise<{ isNew: boolean; task: PlannerTaskResponse }> {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      throw new BadRequestError('Invalid request body');
    }
    const plannerToday = payload.plannerToday;


    if (typeof payload.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(payload.id)) {
      throw new BadRequestError('Invalid id: must be a UUID v4');
    }

    if (typeof payload.title !== 'string' || payload.title.trim() === '') {
      throw new BadRequestError('Invalid title: cannot be blank');
    }

    let note = payload.note;
    if (typeof note === 'string') {
      note = note.trim();
      if (note === '') note = null;
      if (note !== null && note.length > 500) {
        throw new BadRequestError('Invalid note: max 500 characters');
      }
    } else if (note !== undefined && note !== null) {
      throw new BadRequestError('Invalid note');
    }

    if (payload.isImportant !== undefined && typeof payload.isImportant !== 'boolean') {
      throw new BadRequestError('isImportant must be a boolean');
    }
    const isImportant = payload.isImportant ?? false;

    if (payload.schedule !== undefined && payload.schedule !== null) {
      if (typeof payload.schedule !== 'object' || Array.isArray(payload.schedule)) {
        throw new BadRequestError('schedule must be an object');
      }
    }

    const dto: CreateTaskDto = {
      id: payload.id,
      title: payload.title.trim(),
      note: note || null,
      isImportant,
      schedule: null,
    };


    if (payload.schedule) {
      const s = payload.schedule;
      if (!['ONCE', 'INTERVAL_DAYS', 'WEEKDAYS'].includes(s.type)) {
        throw new BadRequestError('Invalid schedule type');
      }

      const startDate = validatePlannerDate(s.startDate, 'startDate');

      if (s.type === 'INTERVAL_DAYS' || s.type === 'WEEKDAYS') {
        if (!plannerToday) {
          throw new BadRequestError('plannerToday is required for recurring schedule creation');
        }
        const today = validatePlannerDate(plannerToday, 'plannerToday');
        if (startDate < today) {
          throw new BadRequestError('Recurring start date cannot be before plannerToday');
        }
      }

      let endDate: string | null = null;
      if (s.endDate !== undefined && s.endDate !== null) {
        endDate = validatePlannerDate(s.endDate, 'endDate');
        if (endDate < startDate) {
          throw new BadRequestError('endDate cannot be before startDate');
        }
      }

      if (s.type === 'ONCE') {
        endDate = startDate; // Frozen schema requires start = end for ONCE
      }

      let scheduledTime = null;
      if (s.scheduledTime !== undefined && s.scheduledTime !== null) {
        if (typeof s.scheduledTime !== 'string' || !TIME_REGEX.test(s.scheduledTime)) {
          throw new BadRequestError('Invalid scheduledTime: must be HH:mm:ss');
        }
        scheduledTime = s.scheduledTime;
      }

      let reminder = null;
      if (s.reminderMinutesBefore !== undefined && s.reminderMinutesBefore !== null) {
        if (!ALLOWED_REMINDERS.includes(s.reminderMinutesBefore)) {
          throw new BadRequestError('Invalid reminderMinutesBefore');
        }
        if (!scheduledTime) {
          throw new BadRequestError('reminderMinutesBefore requires scheduledTime');
        }
        reminder = s.reminderMinutesBefore;
      }

      let intervalDays = null;
      let intervalAnchorDate = null;
      let weekdaysMask = null;

      if (s.type === 'INTERVAL_DAYS') {
        if (typeof s.intervalDays !== 'number' || s.intervalDays < 1 || !Number.isInteger(s.intervalDays)) {
          throw new BadRequestError('intervalDays must be an integer >= 1');
        }
        intervalDays = s.intervalDays;
        intervalAnchorDate = startDate;
      } else if (s.type === 'WEEKDAYS') {
        if (typeof s.weekdaysMask !== 'number' || s.weekdaysMask < 1 || s.weekdaysMask > 127 || !Number.isInteger(s.weekdaysMask)) {
          throw new BadRequestError('weekdaysMask must be an integer between 1 and 127');
        }
        weekdaysMask = s.weekdaysMask;
      }

      // If finite recurring range, must contain at least one occurrence
      if ((s.type === 'INTERVAL_DAYS' || s.type === 'WEEKDAYS') && endDate) {
        if (s.type === 'INTERVAL_DAYS') {
          // intervalAnchorDate === startDate, so it always occurs on startDate.
          // Since endDate >= startDate, it is guaranteed to have at least one occurrence.
        } else if (s.type === 'WEEKDAYS') {
          const dummySchedule: PlannerSchedule = {
            schedule_type: 'WEEKDAYS',
            start_date: startDate,
            end_date: endDate,
            interval_days: null,
            interval_anchor_date: null,
            weekdays_mask: weekdaysMask,
          };

          let hasOccurrence = false;
          let current = startDate;
          let iterations = 0;
          // Check at most 7 days; if no occurrence is found in a full week, there never will be.
          while (current <= endDate && iterations < 7) {
            if (isScheduleOccurringOnDate(dummySchedule, current)) {
              hasOccurrence = true;
              break;
            }
            const d = new Date(current);
            d.setUTCDate(d.getUTCDate() + 1);
            current = d.toISOString().split('T')[0];
            iterations++;
          }
          if (!hasOccurrence) {
            throw new BadRequestError('Finite recurring schedule contains no occurrences');
          }
        }
      }

      dto.schedule = {
        type: s.type,
        startDate,
        endDate,
        scheduledTime,
        intervalDays,
        intervalAnchorDate,
        weekdaysMask,
        reminderMinutesBefore: reminder,
      };
    }

    return await this.tasksRepository.createIdempotent(userId, dto);
  }
}
