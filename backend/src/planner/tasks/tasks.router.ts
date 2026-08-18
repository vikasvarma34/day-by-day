import { Router } from 'express';
import { createAuthMiddleware } from '../../auth/auth.middleware';
import { AuthService } from '../../auth/auth.service';
import { TasksController } from './tasks.controller';
import { TasksService } from './tasks.service';

export function createTasksRouter(
  tasksService: TasksService = new TasksService(),
  authService: AuthService = new AuthService()
): Router {
  const router = Router();
  const tasksController = new TasksController(tasksService);
  const authMiddleware = createAuthMiddleware(authService);

  // Mounted at /tasks in app.ts, so this root corresponds to POST /tasks
  router.post('/', authMiddleware, tasksController.createTask);
  router.patch('/:taskId', authMiddleware, tasksController.updateTask);

  return router;
}
