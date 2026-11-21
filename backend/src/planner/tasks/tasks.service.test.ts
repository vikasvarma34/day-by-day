import { test, describe, it, beforeEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { TasksService } from './tasks.service';
import { TasksRepository } from './tasks.repository';
import { TaskCompletionResponse } from './tasks.types';
import { BadRequestError } from '../../errors/http-errors';

describe('TasksService', () => {
  let mockRepository: any;
  let service: TasksService;
  const mockUserId = 'user-123';

  // Deterministic fixed reference clock (immune to UTC midnight / real clock drift)
  const FIXED_NOW = new Date('2026-06-15T12:00:00.000Z');
  const nowProvider = () => new Date(FIXED_NOW.getTime());

  const today = '2026-06-15';
  const dPast1 = '2026-06-14';
  const dPast2 = '2026-06-13';
  const dPast4 = '2026-06-11';
  const dFuture5 = '2026-06-20';
  const dFuture6 = '2026-06-21';

  beforeEach(() => {
    mockRepository = {
      createIdempotent: mock.fn(),
      findTaskWithSchedules: mock.fn(),
      updateTask: mock.fn(),
      completeTask: mock.fn(),
      undoTask: mock.fn(),
      deleteTask: mock.fn(),
      stopRecurrence: mock.fn(),
    };
    service = new TasksService(mockRepository as any, nowProvider);
  });

  it('rejects missing or invalid UUID', async () => {
    await assert.rejects(
      service.createTask(mockUserId, { id: 'bad', title: 'A' }),
      BadRequestError
    );
  });

  it('rejects blank title', async () => {
    await assert.rejects(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: '   ' }),
      BadRequestError
    );
  });

  it('accepts 1,500 character note and rejects 1,501 character note', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));

    const maxNote = 'a'.repeat(1500);
    await assert.doesNotReject(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: 'T', note: maxNote })
    );

    const tooLongNote = 'a'.repeat(1501);
    await assert.rejects(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: 'T', note: tooLongNote }),
      BadRequestError
    );
  });

  it('creates a Later task with a 1,400-character note without truncation', async () => {
    const note1400 = 'x'.repeat(1400);
    const mockTask = { id: '00000000-0000-4000-a000-000000000001', title: 'Later 1400', note: note1400 };
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: mockTask }));

    const res = await service.createTask(mockUserId, {
      id: '00000000-0000-4000-a000-000000000001',
      title: 'Later 1400',
      note: note1400
    });

    const [, dto] = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(dto.note, note1400);
    assert.equal(dto.note?.length, 1400);
    assert.equal(res.task.note, note1400);
  });

  it('creates a scheduled task with a 1,500-character note without truncation', async () => {
    const note1500 = 'y'.repeat(1500);
    const mockTask = { id: '00000000-0000-4000-a000-000000000002', title: 'Scheduled 1500', note: note1500 };
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: mockTask }));

    const res = await service.createTask(mockUserId, {
      id: '00000000-0000-4000-a000-000000000002',
      title: 'Scheduled 1500',
      note: note1500,
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: today }
    });

    const [, dto] = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(dto.note, note1500);
    assert.equal(dto.note?.length, 1500);
    assert.equal(res.task.note, note1500);
  });

  it('updates an existing task from short note to 1,400 characters without truncation', async () => {
    const note1400 = 'z'.repeat(1400);
    const mockTask = { id: '00000000-0000-4000-a000-000000000003', title: 'Task', note: note1400 };
    mockRepository.updateTask.mock.mockImplementation(async () => ({ task: mockTask, completions: [] }));

    const res = await service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000003', {
      note: note1400
    });

    const [, , dto] = mockRepository.updateTask.mock.calls[0].arguments;
    assert.equal(dto.note, note1400);
    assert.equal(dto.note?.length, 1400);
    assert.equal(res.task.note, note1400);
  });

  it('updates an existing task to exactly 1,500 characters without truncation', async () => {
    const note1500 = 'w'.repeat(1500);
    const mockTask = { id: '00000000-0000-4000-a000-000000000004', title: 'Task', note: note1500 };
    mockRepository.updateTask.mock.mockImplementation(async () => ({ task: mockTask, completions: [] }));

    const res = await service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000004', {
      note: note1500
    });

    const [, , dto] = mockRepository.updateTask.mock.calls[0].arguments;
    assert.equal(dto.note, note1500);
    assert.equal(dto.note?.length, 1500);
    assert.equal(res.task.note, note1500);
  });

  it('rejects 1,501 characters on create and update with HTTP 400 BadRequestError', async () => {
    const note1501 = 'a'.repeat(1501);

    await assert.rejects(
      service.createTask(mockUserId, {
        id: '00000000-0000-4000-a000-000000000005',
        title: 'Task',
        note: note1501
      }),
      (err: any) => {
        assert.ok(err instanceof BadRequestError);
        assert.equal(err.status, 400);
        assert.equal(err.message, 'Invalid note: max 1500 characters');
        return true;
      }
    );

    await assert.rejects(
      service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000005', {
        note: note1501
      }),
      (err: any) => {
        assert.ok(err instanceof BadRequestError);
        assert.equal(err.status, 400);
        assert.equal(err.message, 'Invalid note: max 1500 characters');
        return true;
      }
    );
  });

  it('creates task without schedule (Later task)', async () => {
    const mockTask = { id: '00000000-0000-4000-a000-000000000000', title: 'Later Task', note: 'No schedule' };
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: mockTask }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Later Task',
      note: 'No schedule'
    };
    const result = await service.createTask(mockUserId, payload);
    assert.deepEqual(result, { isNew: true, task: mockTask });
    const [calledUserId, calledDto] = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(calledUserId, mockUserId);
    assert.equal(calledDto.schedule, null);
  });

  it('rejects schedule creation without plannerToday for scheduled tasks (ONCE)', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      schedule: { type: 'ONCE', startDate: dPast2 }
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      /plannerToday is required for scheduled task creation/
    );
  });

  it('rejects schedule creation without plannerToday for recurring schedules', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      schedule: { type: 'INTERVAL_DAYS', startDate: dPast2, intervalDays: 2 }
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      /plannerToday is required for scheduled task creation/
    );
  });

  it('allows historical ONCE start date before plannerToday without reminder', async () => {
    const mockTask = { id: '00000000-0000-4000-a000-000000000000' };
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: mockTask }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: dPast2 }
    };
    const result = await service.createTask(mockUserId, payload);
    assert.equal(mockRepository.createIdempotent.mock.calls.length, 1);
    assert.deepEqual(result, { isNew: true, task: mockTask });
  });

  it('rejects historical ONCE start date before plannerToday when reminder is provided', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: dPast2, scheduledTime: '10:00:00', reminderMinutesBefore: 15 }
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      /Reminders cannot be set for historical tasks/
    );
  });

  it('allows ONCE start date equal to plannerToday', async () => {
    const mockTask = { id: '00000000-0000-4000-a000-000000000000' };
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: mockTask }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: today }
    };
    const result = await service.createTask(mockUserId, payload);
    assert.equal(mockRepository.createIdempotent.mock.calls.length, 1);
    assert.deepEqual(result, { isNew: true, task: mockTask });
  });

  it('allows ONCE start date after plannerToday', async () => {
    const mockTask = { id: '00000000-0000-4000-a000-000000000000' };
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: mockTask }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: dFuture5 }
    };
    const result = await service.createTask(mockUserId, payload);
    assert.equal(mockRepository.createIdempotent.mock.calls.length, 1);
    assert.deepEqual(result, { isNew: true, task: mockTask });
  });

  it('rejects recurring start date before plannerToday', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'WEEKDAYS', startDate: dPast1, weekdaysMask: 65 }
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      /Recurring schedules cannot start before plannerToday/
    );
  });

  it('rejects ONCE schedule with endDate different from startDate (validates start=end implicitly)', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: today, endDate: dFuture5 }
    };
    await service.createTask(mockUserId, payload);
    const [, calledDto] = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(calledDto.schedule.endDate, today);
  });

  it('rejects finite recurring range containing no occurrences', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'WEEKDAYS', startDate: '2050-08-23', endDate: '2050-08-24', weekdaysMask: 1 } // 1 is Monday (Aug 23 2050 is Tue, Aug 24 is Wed)
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      BadRequestError
    );
  });

  it('validates strict body types (null body -> 400)', async () => {
    await assert.rejects(service.createTask(mockUserId, null), BadRequestError);
    await assert.rejects(service.createTask(mockUserId, undefined), BadRequestError);
    await assert.rejects(service.createTask(mockUserId, []), BadRequestError);
  });

  it('validates isImportant strictly', async () => {
    await assert.rejects(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: 'T', isImportant: 'yes' }),
      BadRequestError
    );
  });

  it('validates schedule object strictly', async () => {
    await assert.rejects(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: 'T', schedule: false }),
      BadRequestError
    );
  });

  it('validates scheduledTime format strictly', async () => {
    await assert.rejects(
      service.createTask(mockUserId, {
        id: '00000000-0000-4000-a000-000000000000',
        title: 'T',
        plannerToday: today,
        schedule: { type: 'ONCE', startDate: today, scheduledTime: '' }
      }),
      BadRequestError
    );
  });

  it('evaluates far future finite WEEKDAYS efficiently', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'WEEKDAYS', startDate: '2050-01-01', endDate: '2050-01-05', weekdaysMask: 127 }
    };
    await service.createTask(mockUserId, payload);
    const [, calledDto] = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(calledDto.schedule.endDate, '2050-01-05');
  });

  it('updateTask rejects schedule change if startDate < plannerToday for ONCE', async () => {
    const payload = {
      plannerToday: today,
      effectiveDate: today,
      schedule: { type: 'ONCE', startDate: dPast1 }
    };
    await assert.rejects(
      service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000000', payload),
      /Scheduled start date cannot be before plannerToday/
    );
  });

  it('updateTask allows schedule change if startDate >= plannerToday for ONCE', async () => {
    mockRepository.updateTask.mock.mockImplementation(async () => ({}));
    const payload = {
      plannerToday: today,
      effectiveDate: today,
      schedule: { type: 'ONCE', startDate: today }
    };
    await service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000000', payload);
    assert.equal(mockRepository.updateTask.mock.calls.length, 1);
  });

  it('completeTask rejects missing plannerToday', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', { completedDate: today }),
      /plannerToday is required/
    );
  });

  it('completeTask for scheduled task allows completedDate between scheduledDate and plannerToday', async () => {
    const mockCompletion: TaskCompletionResponse = {
      id: '00000000-0000-4000-a000-000000000099',
      taskId: '00000000-0000-4000-a000-000000000000',
      scheduleId: '00000000-0000-4000-a000-000000000001',
      scheduledDate: dPast4,
      completedDate: dPast2,
      completedAt: `${dPast2}T10:00:00.000Z`,
      titleSnapshot: 'Test Task',
      isImportantSnapshot: true
    };
    mockRepository.completeTask.mock.mockImplementation(async () => mockCompletion);
    const result = await service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
      plannerToday: today,
      completedDate: dPast2,
      scheduleId: '00000000-0000-4000-a000-000000000001',
      scheduledDate: dPast4
    });
    assert.equal(mockRepository.completeTask.mock.calls.length, 1);
    assert.deepEqual(result, mockCompletion);
  });

  it('completeTask for scheduled task rejects completedDate before scheduledDate', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: today,
        completedDate: dPast4,
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: dPast2
      }),
      /completedDate cannot be before scheduledDate/
    );
  });

  it('completeTask for scheduled task rejects completedDate after plannerToday', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: today,
        completedDate: dFuture5,
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: dPast2
      }),
      /completedDate cannot be after plannerToday/
    );
  });

  it('completeTask for future scheduled task allows completedDate equal to plannerToday', async () => {
    const mockCompletion: TaskCompletionResponse = {
      id: '00000000-0000-4000-a000-000000000099',
      taskId: '00000000-0000-4000-a000-000000000000',
      scheduleId: '00000000-0000-4000-a000-000000000001',
      scheduledDate: dFuture5,
      completedDate: today,
      completedAt: `${today}T10:00:00.000Z`,
      titleSnapshot: 'Test Task',
      isImportantSnapshot: true
    };
    mockRepository.completeTask.mock.mockImplementation(async () => mockCompletion);
    const result = await service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
      plannerToday: today,
      completedDate: today,
      scheduleId: '00000000-0000-4000-a000-000000000001',
      scheduledDate: dFuture5
    });
    assert.equal(mockRepository.completeTask.mock.calls.length, 1);
    assert.deepEqual(result, mockCompletion);
  });

  it('completeTask for future scheduled task rejects completedDate before plannerToday or after plannerToday', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: today,
        completedDate: dPast1,
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: dFuture5
      }),
      /completedDate cannot be before scheduledDate/
    );
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: today,
        completedDate: dFuture5,
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: dFuture5
      }),
      /completedDate cannot be after plannerToday/
    );
  });

  it('completeTask for Later task requires completedDate == plannerToday', async () => {
    mockRepository.completeTask.mock.mockImplementation(async () => ({}));
    await service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
      plannerToday: today,
      completedDate: today
    });
    assert.equal(mockRepository.completeTask.mock.calls.length, 1);

    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: today,
        completedDate: dPast1
      }),
      /completedDate for Later task must equal plannerToday/
    );

    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: today,
        completedDate: dFuture5
      }),
      /completedDate for Later task must equal plannerToday/
    );
  });
});
