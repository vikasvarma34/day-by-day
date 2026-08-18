export type ScheduleType = 'ONCE' | 'INTERVAL_DAYS' | 'WEEKDAYS';

export interface PlannerSchedule {
  schedule_type: ScheduleType;
  start_date: string;
  end_date?: string | null;
  interval_days?: number | null;
  interval_anchor_date?: string | null;
  weekdays_mask?: number | null;
}

export const WEEKDAY_MASKS = {
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 4,
  THURSDAY: 8,
  FRIDAY: 16,
  SATURDAY: 32,
  SUNDAY: 64,
} as const;

const MS_PER_DAY = 86_400_000;

function parseDateParts(dateStr: string): [year: number, month: number, day: number] {
  const [y, m, d] = dateStr.split('-').map(Number);
  return [y, m, d];
}

function getWeekdayBit(year: number, month: number, day: number): number {
  const utcDay = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  // UTC Day: 0 = Sunday, 1 = Monday, ..., 6 = Saturday
  return utcDay === 0 ? WEEKDAY_MASKS.SUNDAY : 1 << (utcDay - 1);
}

/**
 * Evaluates whether a planner schedule produces an occurrence on a requested calendar date (YYYY-MM-DD).
 * Pure calendar-date calculation without database access, system time, or server timezone dependencies.
 */
export function isScheduleOccurringOnDate(schedule: PlannerSchedule, requestedDate: string): boolean {
  if (requestedDate < schedule.start_date) {
    return false;
  }

  if (schedule.end_date != null && requestedDate > schedule.end_date) {
    return false;
  }

  switch (schedule.schedule_type) {
    case 'ONCE':
      return requestedDate === schedule.start_date;

    case 'INTERVAL_DAYS': {
      if (!schedule.interval_days || schedule.interval_days < 1 || !schedule.interval_anchor_date) {
        return false;
      }
      const [ay, am, ad] = parseDateParts(schedule.interval_anchor_date);
      const [ry, rm, rd] = parseDateParts(requestedDate);
      const anchorUtc = Date.UTC(ay, am - 1, ad);
      const requestedUtc = Date.UTC(ry, rm - 1, rd);
      const diffDays = Math.round((requestedUtc - anchorUtc) / MS_PER_DAY);
      if (diffDays < 0) {
        return false;
      }
      return diffDays % schedule.interval_days === 0;
    }

    case 'WEEKDAYS': {
      if (schedule.weekdays_mask == null) {
        return false;
      }
      const [ry, rm, rd] = parseDateParts(requestedDate);
      const weekdayBit = getWeekdayBit(ry, rm, rd);
      return (schedule.weekdays_mask & weekdayBit) !== 0;
    }

    default:
      return false;
  }
}
