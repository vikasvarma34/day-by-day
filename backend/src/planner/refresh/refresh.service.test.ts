import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RefreshService } from './refresh.service';
import { RefreshRepository } from './refresh.repository';
import { RawSnapshotRows } from './refresh.types';

test('RefreshService.getSnapshot: maps database rows into clean snapshot DTOs with ISO timestamps', async () => {
  const mockRawData: RawSnapshotRows = {
    tasks: [
      {
        id: 't1',
        title: 'Task 1',
        note: 'Some note',
        is_important: true,
        created_at: new Date('2026-08-01T10:00:00.000Z'),
        updated_at: new Date('2026-08-01T10:00:00.000Z'),
      },
      {
        id: 't2',
        title: 'Task 2',
        note: null,
        is_important: false,
        created_at: '2026-08-02T12:00:00.000Z',
        updated_at: '2026-08-02T12:00:00.000Z',
      },
    ],
    schedules: [
      {
        id: 's1',
        task_id: 't1',
        schedule_type: 'INTERVAL_DAYS',
        start_date: '2026-08-01',
        end_date: null,
        scheduled_time: '09:00:00',
        interval_days: 1,
        interval_anchor_date: '2026-08-01',
        weekdays_mask: null,
        reminder_minutes_before: 15,
        created_at: new Date('2026-08-01T10:00:00.000Z'),
        updated_at: new Date('2026-08-01T10:00:00.000Z'),
      },
    ],
    completions: [
      {
        id: 'c1',
        task_id: 't1',
        schedule_id: 's1',
        scheduled_date: '2026-08-01',
        completed_date: '2026-08-01',
        completed_at: new Date('2026-08-01T09:30:00.000Z'),
        title_snapshot: 'Task 1 Snapshot',
        is_important_snapshot: true,
      },
    ],
  };

  const mockRepo = {
    getSnapshotData: async (_userId: string) => mockRawData,
  } as unknown as RefreshRepository;

  const service = new RefreshService(mockRepo);
  const result = await service.getSnapshot('user-1');

  assert.equal(result.tasks.length, 2);
  assert.deepEqual(result.tasks[0], {
    id: 't1',
    title: 'Task 1',
    note: 'Some note',
    isImportant: true,
    createdAt: '2026-08-01T10:00:00.000Z',
    updatedAt: '2026-08-01T10:00:00.000Z',
  });
  assert.deepEqual(result.tasks[1], {
    id: 't2',
    title: 'Task 2',
    note: null,
    isImportant: false,
    createdAt: '2026-08-02T12:00:00.000Z',
    updatedAt: '2026-08-02T12:00:00.000Z',
  });

  assert.equal(result.schedules.length, 1);
  assert.deepEqual(result.schedules[0], {
    id: 's1',
    taskId: 't1',
    scheduleType: 'INTERVAL_DAYS',
    startDate: '2026-08-01',
    endDate: null,
    scheduledTime: '09:00:00',
    intervalDays: 1,
    intervalAnchorDate: '2026-08-01',
    weekdaysMask: null,
    reminderMinutesBefore: 15,
    createdAt: '2026-08-01T10:00:00.000Z',
    updatedAt: '2026-08-01T10:00:00.000Z',
  });

  assert.equal(result.completions.length, 1);
  assert.deepEqual(result.completions[0], {
    id: 'c1',
    taskId: 't1',
    scheduleId: 's1',
    scheduledDate: '2026-08-01',
    completedDate: '2026-08-01',
    completedAt: '2026-08-01T09:30:00.000Z',
    titleSnapshot: 'Task 1 Snapshot',
    isImportantSnapshot: true,
  });
});

test('RefreshService.getSnapshot: handles empty snapshot', async () => {
  const mockRawData: RawSnapshotRows = {
    tasks: [],
    schedules: [],
    completions: [],
  };

  const mockRepo = {
    getSnapshotData: async (_userId: string) => mockRawData,
  } as unknown as RefreshRepository;

  const service = new RefreshService(mockRepo);
  const result = await service.getSnapshot('user-empty');

  assert.deepEqual(result, {
    tasks: [],
    schedules: [],
    completions: [],
  });
});
