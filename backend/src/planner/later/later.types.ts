export interface LaterTaskItem {
  taskId: string;
  title: string;
  note: string | null;
  isImportant: boolean;
}

export interface PlannerLaterResponse {
  items: LaterTaskItem[];
}

export interface LaterTaskRow {
  id: string;
  title: string;
  note: string | null;
  is_important: boolean;
}
