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

  const today = new Date().toISOString().split('T')[0];
  const dPast1 = new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  const dPast2 = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  const dPast4 = new Date(Date.now() - 4 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  const dFuture5 = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  const dFuture6 = new Date(Date.now() + 6 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

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
    service = new TasksService(mockRepository as any);
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

  it('rejects too long note', async () => {
    const longNote = 'a'.repeat(2001);
    await assert.rejects(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: 'T', note: longNote }),
      BadRequestError
    );
  });

  it('creates task without schedule (Later task)', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Later Task',
      note: 'No schedule'
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[0], mockUserId);
    assert.equal(callArgs[1].schedule, null);
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
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: dPast2 }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.startDate, dPast2);
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
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: today }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.startDate, today);
  });

  it('allows ONCE start date after plannerToday', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: today,
      schedule: { type: 'ONCE', startDate: dFuture5 }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.startDate, dFuture5);
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
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[0], mockUserId);
    assert.equal(callArgs[1].schedule.endDate, today);
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
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.endDate, '2050-01-05');
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
        completedDate: '2026-08-01',
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
