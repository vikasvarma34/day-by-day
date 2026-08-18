import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HistoryService } from './history.service';
import { HistoryRepository } from './history.repository';
import { HistoryRow } from './history.types';

test('HistoryService.getHistory: groups rows by completed_date and maps items to ISO timestamps', async () => {
  const mockRows: HistoryRow[] = [
    {
      completion_id: 'c1',
      task_id: 't1',
      title: 'Renew passport',
      completed_date: '2026-08-17',
      completed_at: new Date('2026-08-17T14:30:00.000Z'),
    },
    {
      completion_id: 'c2',
      task_id: 't2',
      title: 'Buy medicine',
      completed_date: '2026-08-17',
      completed_at: new Date('2026-08-17T10:00:00.000Z'),
    },
    {
      completion_id: 'c3',
      task_id: 't3',
      title: 'Pay electricity bill',
      completed_date: '2026-08-15',
      completed_at: '2026-08-15T09:15:00.000Z',
    },
  ];

  const mockRepo = {
    findHistoryCompletions: async (_userId: string, _plannerToday: string, _search?: string) =>
      mockRows,
  } as unknown as HistoryRepository;

  const service = new HistoryService(mockRepo);
  const response = await service.getHistory('u1', '2026-08-18');

  assert.equal(response.groups.length, 2);

  // Group 1: 2026-08-17
  assert.equal(response.groups[0].date, '2026-08-17');
  assert.equal(response.groups[0].items.length, 2);
  assert.deepEqual(response.groups[0].items[0], {
    taskId: 't1',
    completionId: 'c1',
    title: 'Renew passport',
    completedAt: '2026-08-17T14:30:00.000Z',
  });
  assert.deepEqual(response.groups[0].items[1], {
    taskId: 't2',
    completionId: 'c2',
    title: 'Buy medicine',
    completedAt: '2026-08-17T10:00:00.000Z',
  });

  // Group 2: 2026-08-15
  assert.equal(response.groups[1].date, '2026-08-15');
  assert.equal(response.groups[1].items.length, 1);
  assert.deepEqual(response.groups[1].items[0], {
    taskId: 't3',
    completionId: 'c3',
    title: 'Pay electricity bill',
    completedAt: '2026-08-15T09:15:00.000Z',
  });
});

test('HistoryService.getHistory: returns empty groups array when repository returns no rows', async () => {
  const mockRepo = {
    findHistoryCompletions: async () => [],
  } as unknown as HistoryRepository;

  const service = new HistoryService(mockRepo);
  const response = await service.getHistory('u1', '2026-08-18');

  assert.deepEqual(response, { groups: [] });
});
