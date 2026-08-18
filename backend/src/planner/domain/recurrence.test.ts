import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  isScheduleOccurringOnDate,
  PlannerSchedule,
  WEEKDAY_MASKS,
} from './recurrence';

test('ONCE recurrence: occurs on its exact date and rejects dates before or after', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'ONCE',
    start_date: '2026-08-18',
    end_date: '2026-08-18',
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-18'), true, 'Exact date must occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-17'), false, 'Date before start must not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-19'), false, 'Date after start must not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2025-08-18'), false, 'Past year date must not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2027-08-18'), false, 'Future year date must not occur');
});

test('INTERVAL_DAYS recurrence: every-day interval (interval_days = 1)', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-03-01',
    interval_days: 1,
    interval_anchor_date: '2026-03-01',
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-03-01'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-03-02'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-03-03'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-03-31'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-02-28'), false, 'Date before start must not occur');
});

test('INTERVAL_DAYS recurrence: every-2-days cadence', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-05-10',
    interval_days: 2,
    interval_anchor_date: '2026-05-10',
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-05-10'), true, 'Anchor day occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-05-11'), false, 'Day +1 does not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-05-12'), true, 'Day +2 occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-05-13'), false, 'Day +3 does not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-05-14'), true, 'Day +4 occurs');
});

test('INTERVAL_DAYS recurrence: anchor earlier than start date preserves original cadence', () => {
  // Anchor on Jan 1 (Thursday), start on Jan 2 (Friday), interval = 2 days
  // Cadence: Jan 1, Jan 3, Jan 5, Jan 7...
  // Jan 2 is start_date but not on the original anchor cadence -> false
  // First valid occurrence inside segment is Jan 3 -> true
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-01-02',
    interval_days: 2,
    interval_anchor_date: '2026-01-01',
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-01'), false, 'Anchor date before start_date is rejected by start boundary');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-02'), false, 'Start date is off-cadence and must not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-03'), true, 'First occurrence on cadence within segment occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-04'), false, 'Off-cadence date does not occur');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-05'), true, 'Second occurrence on cadence occurs');
});

test('INTERVAL_DAYS recurrence: date before segment start rejected', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-06-15',
    interval_days: 3,
    interval_anchor_date: '2026-06-03',
  };

  // Cadence is Jun 3, Jun 6, Jun 9, Jun 12, Jun 15, Jun 18...
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-06-12'), false, 'Cadence date before start_date is rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-06-14'), false, 'Arbitrary date before start_date is rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-06-15'), true, 'Start date on cadence is accepted');
});

test('INTERVAL_DAYS recurrence: finite end date is inclusive and date after finite end rejected', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-04-01',
    end_date: '2026-04-11',
    interval_days: 5,
    interval_anchor_date: '2026-04-01',
  };

  // Cadence: Apr 01, Apr 06, Apr 11, Apr 16...
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-01'), true, 'Start date occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-06'), true, 'Mid-segment date occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-11'), true, 'Finite end date is inclusive');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-12'), false, 'Date immediately after end rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-16'), false, 'Cadence date after end rejected');
});

test('INTERVAL_DAYS recurrence: open-ended recurrence works far into future', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-01-01',
    end_date: null,
    interval_days: 10,
    interval_anchor_date: '2026-01-01',
  };

  // 100 days after Jan 1, 2026 is Apr 11, 2026 (Jan: 30 remaining, Feb: 28, Mar: 31, Apr: 11 -> 30+28+31+11 = 100)
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-11'), true, '100 days after anchor occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-04-12'), false, '101 days after anchor does not occur');
});

test('INTERVAL_DAYS recurrence: cadence across a month boundary', () => {
  // Jan 30, interval = 3 days -> Jan 30 (anchor/start), Feb 2 (Jan has 31 days: +2 days to Feb 1, +3 to Feb 2), Feb 5
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2026-01-30',
    interval_days: 3,
    interval_anchor_date: '2026-01-30',
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-30'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-31'), false);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-02-01'), false);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-02-02'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-02-05'), true);
});

test('INTERVAL_DAYS recurrence: cadence across a year boundary', () => {
  // Dec 30, 2025 with interval = 4 days -> Dec 30, 2025 -> Jan 3, 2026 -> Jan 7, 2026
  const schedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2025-12-30',
    interval_days: 4,
    interval_anchor_date: '2025-12-30',
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2025-12-30'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2025-12-31'), false);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-01'), false);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-02'), false);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-03'), true);
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-01-07'), true);
});

test('INTERVAL_DAYS recurrence: leap-year date arithmetic behaves correctly', () => {
  // 2024 is a leap year (February has 29 days)
  const leapSchedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2024-02-28',
    interval_days: 2,
    interval_anchor_date: '2024-02-28',
  };

  assert.equal(isScheduleOccurringOnDate(leapSchedule, '2024-02-28'), true);
  assert.equal(isScheduleOccurringOnDate(leapSchedule, '2024-02-29'), false);
  assert.equal(isScheduleOccurringOnDate(leapSchedule, '2024-03-01'), true, 'Feb 28 + 2 days in leap year is Mar 1');
  assert.equal(isScheduleOccurringOnDate(leapSchedule, '2024-03-02'), false);
  assert.equal(isScheduleOccurringOnDate(leapSchedule, '2024-03-03'), true);

  // 2025 is a non-leap year (February has 28 days)
  const nonLeapSchedule: PlannerSchedule = {
    schedule_type: 'INTERVAL_DAYS',
    start_date: '2025-02-28',
    interval_days: 2,
    interval_anchor_date: '2025-02-28',
  };

  assert.equal(isScheduleOccurringOnDate(nonLeapSchedule, '2025-02-28'), true);
  assert.equal(isScheduleOccurringOnDate(nonLeapSchedule, '2025-03-01'), false);
  assert.equal(isScheduleOccurringOnDate(nonLeapSchedule, '2025-03-02'), true, 'Feb 28 + 2 days in non-leap year is Mar 2');
});

test('WEEKDAYS recurrence: single selected weekday (Wednesday = 4)', () => {
  // 2026-08-19 is a Wednesday
  // 2026-08-20 is Thursday, 2026-08-26 is Wednesday
  const schedule: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-01',
    weekdays_mask: WEEKDAY_MASKS.WEDNESDAY, // 4
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-19'), true, 'Wednesday is accepted');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-20'), false, 'Thursday is rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-26'), true, 'Next Wednesday is accepted');
});

test('WEEKDAYS recurrence: multiple weekday mask (Monday, Wednesday, Friday = 21)', () => {
  // Monday = 1, Wednesday = 4, Friday = 16 -> 21
  const schedule: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-17', // Monday
    weekdays_mask: WEEKDAY_MASKS.MONDAY | WEEKDAY_MASKS.WEDNESDAY | WEEKDAY_MASKS.FRIDAY, // 21
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-17'), true, 'Monday occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-18'), false, 'Tuesday rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-19'), true, 'Wednesday occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-20'), false, 'Thursday rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-21'), true, 'Friday occurs');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-22'), false, 'Saturday rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-23'), false, 'Sunday rejected');
});

test('WEEKDAYS recurrence: start boundary respected even if weekday matches', () => {
  // Schedule starts on Wednesday 2026-08-19 with Monday & Wednesday mask
  // Monday 2026-08-17 matches mask, but is before start_date
  const schedule: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-19',
    weekdays_mask: WEEKDAY_MASKS.MONDAY | WEEKDAY_MASKS.WEDNESDAY,
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-17'), false, 'Matching Monday before start_date is rejected');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-19'), true, 'Start date Wednesday matches and is accepted');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-24'), true, 'Subsequent Monday after start_date is accepted');
});

test('WEEKDAYS recurrence: finite end boundary is inclusive and date after end rejected', () => {
  // Schedule active from 2026-08-17 (Mon) to 2026-08-21 (Fri) with Mon/Wed/Fri mask
  const schedule: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-17',
    end_date: '2026-08-21',
    weekdays_mask: WEEKDAY_MASKS.MONDAY | WEEKDAY_MASKS.WEDNESDAY | WEEKDAY_MASKS.FRIDAY,
  };

  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-21'), true, 'Matching Friday on end_date is inclusive/accepted');
  assert.equal(isScheduleOccurringOnDate(schedule, '2026-08-24'), false, 'Matching Monday after end_date is rejected');
});

test('WEEKDAYS recurrence: open-ended weekday recurrence works without end_date', () => {
  const schedule: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-01-01',
    end_date: null,
    weekdays_mask: WEEKDAY_MASKS.TUESDAY, // 2
  };

  // 2027-01-05 is a Tuesday in 2027
  assert.equal(isScheduleOccurringOnDate(schedule, '2027-01-05'), true, 'Future Tuesday is accepted');
  assert.equal(isScheduleOccurringOnDate(schedule, '2027-01-06'), false, 'Future Wednesday is rejected');
});

test('WEEKDAYS recurrence: correct behavior across a Sunday -> Monday week boundary', () => {
  // 2026-08-23 is Sunday (mask 64), 2026-08-24 is Monday (mask 1)
  const sundayOnly: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-01',
    weekdays_mask: WEEKDAY_MASKS.SUNDAY, // 64
  };

  assert.equal(isScheduleOccurringOnDate(sundayOnly, '2026-08-23'), true, 'Sunday occurs');
  assert.equal(isScheduleOccurringOnDate(sundayOnly, '2026-08-24'), false, 'Monday does not occur for Sunday-only mask');

  const mondayOnly: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-01',
    weekdays_mask: WEEKDAY_MASKS.MONDAY, // 1
  };

  assert.equal(isScheduleOccurringOnDate(mondayOnly, '2026-08-23'), false, 'Sunday does not occur for Monday-only mask');
  assert.equal(isScheduleOccurringOnDate(mondayOnly, '2026-08-24'), true, 'Monday occurs for Monday-only mask');

  const weekendOnly: PlannerSchedule = {
    schedule_type: 'WEEKDAYS',
    start_date: '2026-08-01',
    weekdays_mask: WEEKDAY_MASKS.SATURDAY | WEEKDAY_MASKS.SUNDAY, // 32 + 64 = 96
  };

  assert.equal(isScheduleOccurringOnDate(weekendOnly, '2026-08-22'), true, 'Saturday occurs');
  assert.equal(isScheduleOccurringOnDate(weekendOnly, '2026-08-23'), true, 'Sunday occurs');
  assert.equal(isScheduleOccurringOnDate(weekendOnly, '2026-08-24'), false, 'Monday does not occur for weekend mask');
});
