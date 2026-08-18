import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../../auth/auth.middleware';
import { UnauthorizedError } from '../../errors/http-errors';
import { TasksService } from './tasks.service';

export class TasksController {
  constructor(private readonly tasksService: TasksService = new TasksService()) {}

  createTask = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const { isNew, task } = await this.tasksService.createTask(
        req.user.id,
        req.body
      );

      res.status(isNew ? 201 : 200).json({ task });
    } catch (err) {
      next(err);
    }
  };

  updateTask = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const taskId = req.params.taskId as string;
      const task = await this.tasksService.updateTask(
        req.user.id,
        taskId,
        req.body
      );

      res.status(200).json({ task });
    } catch (err) {
      next(err);
    }
  };
}
