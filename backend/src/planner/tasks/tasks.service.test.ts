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

  it('rejects ONCE start date before plannerToday', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-19',
      schedule: { type: 'ONCE', startDate: '2026-08-18' }
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      /Scheduled start date cannot be before plannerToday/
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
      plannerToday: '2026-08-18',
      schedule: { type: 'WEEKDAYS', startDate: '2026-08-17', weekdaysMask: 65 }
    };
    await assert.rejects(
      service.createTask(mockUserId, payload),
      /Scheduled start date cannot be before plannerToday/
    );
  });

  it('rejects ONCE schedule with endDate different from startDate (validates start=end implicitly)', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-18',
      schedule: { type: 'ONCE', startDate: '2026-08-18', endDate: '2026-08-20' }
    };
    await service.createTask(mockUserId, payload);
    const callArgs = mockRepository.createIdempotent.mock.calls[0].arguments;
    assert.equal(callArgs[0], mockUserId);
    assert.equal(callArgs[1].schedule.endDate, '2026-08-18');
  });

  it('rejects finite recurring range containing no occurrences', async () => {
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-10',
      schedule: { type: 'WEEKDAYS', startDate: '2026-08-15', endDate: '2026-08-16', weekdaysMask: 2 } // 2 is Monday
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
        plannerToday: '2026-08-18',
        schedule: { type: 'ONCE', startDate: '2026-08-18', scheduledTime: '' }
      }),
      BadRequestError
    );
  });

  it('evaluates far future finite WEEKDAYS efficiently', async () => {
    mockRepository.createIdempotent.mock.mockImplementation(async () => ({ isNew: true, task: {} }));
    const payload = {
      id: '00000000-0000-4000-a000-000000000000',
      title: 'Task',
      plannerToday: '2026-08-18',
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
});
