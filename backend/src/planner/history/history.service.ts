import { HistoryRepository } from './history.repository';
import { HistoryGroup, HistoryItem, PlannerHistoryResponse } from './history.types';

export class HistoryService {
  constructor(private readonly historyRepository: HistoryRepository = new HistoryRepository()) {}

  async getHistory(
    userId: string,
    plannerToday: string,
    search?: string
  ): Promise<PlannerHistoryResponse> {
    const rows = await this.historyRepository.findHistoryCompletions(userId, plannerToday, search);

    const groups: HistoryGroup[] = [];
    let currentGroup: HistoryGroup | null = null;

    for (const row of rows) {
      const item: HistoryItem = {
        taskId: row.task_id,
        completionId: row.completion_id,
        title: row.title,
        completedAt:
          row.completed_at instanceof Date
            ? row.completed_at.toISOString()
            : new Date(row.completed_at).toISOString(),
      };

      if (!currentGroup || currentGroup.date !== row.completed_date) {
        currentGroup = {
          date: row.completed_date,
          items: [item],
        };
        groups.push(currentGroup);
      } else {
        currentGroup.items.push(item);
      }
    }

    return { groups };
  }
}
