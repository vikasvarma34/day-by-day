import { test, describe, it, beforeEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { TasksService } from './tasks.service';
import { TasksRepository } from './tasks.repository';
import { BadRequestError } from '../../errors/http-errors';

describe('TasksService', () => {
  let mockRepository: any;
  let service: TasksService;
  const mockUserId = 'user-123';

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
    const longNote = 'a'.repeat(501);
    await assert.rejects(
      service.createTask(mockUserId, { id: '00000000-0000-4000-a000-000000000000', title: 'A', note: longNote }),
      BadRequestError
    );
  });

  it('allows Later task creation without plannerToday', async () => {
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
      schedule: { type: 'ONCE', startDate: '2026-08-18' }
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
      schedule: { type: 'INTERVAL_DAYS', startDate: '2026-08-18', intervalDays: 2 }
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
      plannerToday: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-18' }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.startDate, '2026-08-18');
  });

  it('rejects historical ONCE start date before plannerToday when reminder is provided', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-18', scheduledTime: '10:00:00', reminderMinutesBefore: 15 }
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
      plannerToday: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-19' }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.startDate, '2026-08-19');
  });

  it('allows ONCE start date after plannerToday', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-20' }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.startDate, '2026-08-20');
  });

  it('rejects recurring start date before plannerToday', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-20',
      schedule: { type: 'WEEKDAYS', startDate: '2026-08-19', weekdaysMask: 65 }
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
      plannerToday: '2026-08-20',
      schedule: { type: 'ONCE', startDate: '2026-08-20', endDate: '2026-08-22' }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[0], mockUserId);
    assert.equal(callArgs[1].schedule.endDate, '2026-08-20');
  });

  it('rejects finite recurring range containing no occurrences', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-20',
      schedule: { type: 'WEEKDAYS', startDate: '2026-08-25', endDate: '2026-08-26', weekdaysMask: 1 } // 1 is Monday (Aug 25 is Tue, Aug 26 is Wed)
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
        plannerToday: '2026-08-20',
        schedule: { type: 'ONCE', startDate: '2026-08-20', scheduledTime: '' }
      }),
      BadRequestError
    );
  });

  it('evaluates far future finite WEEKDAYS efficiently', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-20',
      schedule: { type: 'WEEKDAYS', startDate: '2050-01-01', endDate: '2050-01-05', weekdaysMask: 127 }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[1].schedule.endDate, '2050-01-05');
  });

  it('updateTask rejects schedule change if startDate < plannerToday for ONCE', async () => {
    const payload = {
      plannerToday: '2026-08-19',
      effectiveDate: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-18' }
    };
    await assert.rejects(
      service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000000', payload),
      /Scheduled start date cannot be before plannerToday/
    );
  });

  it('updateTask allows schedule change if startDate >= plannerToday for ONCE', async () => {
    mockRepository.updateTask.mock.mockImplementation(async () => ({}));
    const payload = {
      plannerToday: '2026-08-19',
      effectiveDate: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-19' }
    };
    await service.updateTask(mockUserId, '00000000-0000-4000-a000-000000000000', payload);
    assert.equal(mockRepository.updateTask.mock.calls.length, 1);
  });

  it('completeTask rejects missing plannerToday', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', { completedDate: '2026-08-20' }),
      /plannerToday is required/
    );
  });

  it('completeTask for scheduled task allows completedDate between scheduledDate and plannerToday', async () => {
    mockRepository.completeTask.mock.mockImplementation(async () => {});
    await service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
      plannerToday: '2026-08-20',
      completedDate: '2026-08-18',
      scheduleId: '00000000-0000-4000-a000-000000000001',
      scheduledDate: '2026-08-16'
    });
    assert.equal(mockRepository.completeTask.mock.calls.length, 1);
  });

  it('completeTask for scheduled task rejects completedDate before scheduledDate', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-15',
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: '2026-08-16'
      }),
      /completedDate cannot be before scheduledDate/
    );
  });

  it('completeTask for scheduled task rejects completedDate after plannerToday', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-21',
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: '2026-08-16'
      }),
      /completedDate cannot be after plannerToday/
    );
  });

  it('completeTask for future scheduled task allows completedDate equal to plannerToday', async () => {
    mockRepository.completeTask.mock.mockImplementation(async () => {});
    await service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
      plannerToday: '2026-08-20',
      completedDate: '2026-08-20',
      scheduleId: '00000000-0000-4000-a000-000000000001',
      scheduledDate: '2026-08-25'
    });
    assert.equal(mockRepository.completeTask.mock.calls.length, 1);
  });

  it('completeTask for future scheduled task rejects completedDate before plannerToday or after plannerToday', async () => {
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-19',
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: '2026-08-25'
      }),
      /completedDate cannot be before scheduledDate/
    );
    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-25',
        scheduleId: '00000000-0000-4000-a000-000000000001',
        scheduledDate: '2026-08-25'
      }),
      /completedDate cannot be after plannerToday/
    );
  });

  it('completeTask for Later task requires completedDate == plannerToday', async () => {
    mockRepository.completeTask.mock.mockImplementation(async () => {});
    await service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
      plannerToday: '2026-08-20',
      completedDate: '2026-08-20'
    });
    assert.equal(mockRepository.completeTask.mock.calls.length, 1);

    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-19'
      }),
      /completedDate for Later task must equal plannerToday/
    );

    await assert.rejects(
      service.completeTask(mockUserId, '00000000-0000-4000-a000-000000000000', {
        plannerToday: '2026-08-20',
        completedDate: '2026-08-21'
      }),
      /completedDate for Later task must equal plannerToday/
    );
  });
});
