import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DayService } from './day.service';
import { DayRepository } from './day.repository';
import { CandidateScheduleRow } from './day.types';

test('DayService.getDay: organizes occurrences into timed (sorted), anytime, and completed sections', async () => {
  const mockCandidateRows: CandidateScheduleRow[] = [
    // 1. Timed 18:00:00 (Evening)
    {
      task_id: 't1',
      title: 'Gym',
      note: 'Leg day',
      is_important: true,
      schedule_id: 's1',
      schedule_type: 'WEEKDAYS',
      start_date: '2026-08-01',
      end_date: null,
      scheduled_time: '18:00:00',
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: 127, // Everyday
      reminder_minutes_before: 15,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
    // 2. Timed 09:00:00 (Morning - should be sorted before Gym)
    {
      task_id: 't2',
      title: 'Morning Standup',
      note: null,
      is_important: false,
      schedule_id: 's2',
      schedule_type: 'ONCE',
      start_date: '2026-08-18',
      end_date: '2026-08-18',
      scheduled_time: '09:00:00',
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: null,
      reminder_minutes_before: null,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
    // 3. Anytime task (no scheduled_time, not completed)
    {
      task_id: 't3',
      title: 'Laundry',
      note: 'Wash whites',
      is_important: false,
      schedule_id: 's3',
      schedule_type: 'INTERVAL_DAYS',
      start_date: '2026-08-01',
      end_date: null,
      scheduled_time: null,
      interval_days: 1,
      interval_anchor_date: '2026-08-01',
      weekdays_mask: null,
      reminder_minutes_before: null,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
    // 4. Completed task (has completion_id and completed_at)
    {
      task_id: 't4',
      title: 'Meditation',
      note: null,
      is_important: true,
      schedule_id: 's4',
      schedule_type: 'ONCE',
      start_date: '2026-08-18',
      end_date: '2026-08-18',
      scheduled_time: '07:00:00',
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: null,
      reminder_minutes_before: null,
      completion_id: 'c1',
      completed_date: '2026-08-18',
      completed_at: new Date('2026-08-18T07:15:00.000Z'),
    },
    // 5. Non-matching recurrence for this date (INTERVAL_DAYS with start_date in future or odd interval)
    {
      task_id: 't5',
      title: 'Doctor Appointment',
      note: null,
      is_important: false,
      schedule_id: 's5',
      schedule_type: 'INTERVAL_DAYS',
      start_date: '2026-08-01',
      end_date: null,
      scheduled_time: '14:00:00',
      interval_days: 10,
      interval_anchor_date: '2026-08-01', // Occurs Aug 1, Aug 11, Aug 21... not Aug 18
      weekdays_mask: null,
      reminder_minutes_before: null,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
    // 6. Anytime task 2
    {
      task_id: 't6',
      title: 'Read Docs',
      note: null,
      is_important: false,
      schedule_id: 's6',
      schedule_type: 'ONCE',
      start_date: '2026-08-18',
      end_date: '2026-08-18',
      scheduled_time: null,
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: null,
      reminder_minutes_before: null,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
  ];

  const mockRepo = {
    findCandidateSchedulesForDate: async (_userId: string, _date: string) => mockCandidateRows,
  } as unknown as DayRepository;

  const service = new DayService(mockRepo);
  const result = await service.getDay('u1', '2026-08-18');

  assert.equal(result.date, '2026-08-18');

  // Timed section (sorted ascending by scheduledTime: 09:00:00 before 18:00:00)
  assert.equal(result.timed.length, 2);
  assert.equal(result.timed[0].title, 'Morning Standup');
  assert.equal(result.timed[0].scheduledTime, '09:00:00');
  assert.equal(result.timed[1].title, 'Gym');
  assert.equal(result.timed[1].scheduledTime, '18:00:00');

  // Anytime section (t3 and t6)
  assert.equal(result.anytime.length, 2);
  assert.equal(result.anytime[0].title, 'Laundry');
  assert.equal(result.anytime[0].scheduledTime, null);
  assert.equal(result.anytime[1].title, 'Read Docs');
  assert.equal(result.anytime[1].scheduledTime, null);

  // Completed section (t4 with formatted completion ISO timestamp)
  assert.equal(result.completed.length, 1);
  assert.equal(result.completed[0].title, 'Meditation');
  assert.equal(result.completed[0].completion?.id, 'c1');
  assert.equal(result.completed[0].completion?.completedDate, '2026-08-18');
  assert.equal(result.completed[0].completion?.completedAt, '2026-08-18T07:15:00.000Z');
});
