import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PlannerController } from './planner.controller';
import { PlannerRepository } from './planner.repository';
import { AuthenticatedRequest } from '../auth/auth.middleware';
import { CandidateScheduleRow } from './types';

test('PlannerController.getDay: organizes occurrences into timed (sorted), anytime, and completed sections', async () => {
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
      weekdays_mask: 2, // Tuesday (2026-08-18 is Tuesday)
      reminder_minutes_before: 15,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
    // 2. Timed 08:30:00 (Morning)
    {
      task_id: 't2',
      title: 'Breakfast',
      note: null,
      is_important: false,
      schedule_id: 's2',
      schedule_type: 'ONCE',
      start_date: '2026-08-18',
      end_date: '2026-08-18',
      scheduled_time: '08:30:00',
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: null,
      reminder_minutes_before: null,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
    // 3. Anytime task (scheduled_time is null)
    {
      task_id: 't3',
      title: 'Read Book',
      note: 'Chapter 4',
      is_important: false,
      schedule_id: 's3',
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
    // 4. Completed task with timed schedule (must appear in completed, NOT timed)
    {
      task_id: 't4',
      title: 'Morning Standup',
      note: null,
      is_important: true,
      schedule_id: 's4',
      schedule_type: 'ONCE',
      start_date: '2026-08-18',
      end_date: '2026-08-18',
      scheduled_time: '09:00:00',
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: null,
      reminder_minutes_before: 5,
      completion_id: 'c1',
      completed_date: '2026-08-18',
      completed_at: new Date('2026-08-18T09:05:00.000Z'),
    },
    // 5. Non-occurrence candidate (WEEKDAYS mask is Wednesday = 4, but 2026-08-18 is Tuesday) -> should be filtered out
    {
      task_id: 't5',
      title: 'Wednesday Meeting',
      note: null,
      is_important: false,
      schedule_id: 's5',
      schedule_type: 'WEEKDAYS',
      start_date: '2026-08-01',
      end_date: null,
      scheduled_time: '14:00:00',
      interval_days: null,
      interval_anchor_date: null,
      weekdays_mask: 4, // Wednesday only
      reminder_minutes_before: null,
      completion_id: null,
      completed_date: null,
      completed_at: null,
    },
  ];

  const mockRepo = {
    findCandidateSchedulesForDate: async (_userId: string, _date: string) => mockCandidateRows,
  } as unknown as PlannerRepository;

  const controller = new PlannerController(mockRepo);

  let responseStatus = 0;
  let responseData: any = null;

  const req = {
    user: { id: 'u1', email: 'test@example.com' },
    params: { date: '2026-08-18' },
  } as unknown as AuthenticatedRequest;

  const res = {
    status(code: number) {
      responseStatus = code;
      return this;
    },
    json(data: any) {
      responseData = data;
      return this;
    },
  } as any;

  await controller.getDay(req, res, (err) => {
    if (err) throw err;
  });

  assert.equal(responseStatus, 200);
  assert.equal(responseData.date, '2026-08-18');

  // Timed must contain 2 items, sorted ascending by scheduledTime: 08:30:00 then 18:00:00
  assert.equal(responseData.timed.length, 2);
  assert.equal(responseData.timed[0].title, 'Breakfast');
  assert.equal(responseData.timed[0].scheduledTime, '08:30:00');
  assert.equal(responseData.timed[0].completion, null);

  assert.equal(responseData.timed[1].title, 'Gym');
  assert.equal(responseData.timed[1].scheduledTime, '18:00:00');
  assert.equal(responseData.timed[1].completion, null);

  // Anytime must contain 1 item
  assert.equal(responseData.anytime.length, 1);
  assert.equal(responseData.anytime[0].title, 'Read Book');
  assert.equal(responseData.anytime[0].scheduledTime, null);
  assert.equal(responseData.anytime[0].completion, null);

  // Completed must contain 1 item (t4), with its completion details attached
  assert.equal(responseData.completed.length, 1);
  assert.equal(responseData.completed[0].title, 'Morning Standup');
  assert.equal(responseData.completed[0].scheduledTime, '09:00:00');
  assert.deepEqual(responseData.completed[0].completion, {
    id: 'c1',
    completedDate: '2026-08-18',
    completedAt: '2026-08-18T09:05:00.000Z',
  });

  // t5 (Wednesday meeting) must not appear anywhere
  const allTaskIds = [
    ...responseData.timed.map((i: any) => i.taskId),
    ...responseData.anytime.map((i: any) => i.taskId),
    ...responseData.completed.map((i: any) => i.taskId),
  ];
  assert.equal(allTaskIds.includes('t5'), false, 'Non-occurring schedule must be excluded');
});
