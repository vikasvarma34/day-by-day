export interface HistoryItem {
  taskId: string;
  completionId: string;
  title: string;
  completedAt: string;
}

export interface HistoryGroup {
  date: string;
  items: HistoryItem[];
}

export interface PlannerHistoryResponse {
  groups: HistoryGroup[];
}

export interface HistoryRow {
  completion_id: string;
  task_id: string;
  title: string;
  completed_date: string;
  completed_at: Date | string;
}
