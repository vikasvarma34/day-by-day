import { LaterRepository } from './later.repository';
import { PlannerLaterResponse } from './later.types';

export class LaterService {
  constructor(private readonly laterRepository: LaterRepository = new LaterRepository()) {}

  async getLater(userId: string): Promise<PlannerLaterResponse> {
    const rows = await this.laterRepository.findLaterTasks(userId);

    return {
      items: rows.map((row) => ({
        taskId: row.id,
        title: row.title,
        note: row.note ?? null,
        isImportant: row.is_important,
      })),
    };
  }
}
