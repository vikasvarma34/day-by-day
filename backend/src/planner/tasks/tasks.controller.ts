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
      const result = await this.tasksService.updateTask(
        req.user.id,
        taskId,
        req.body
      );

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  };

  completeTask = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) throw new UnauthorizedError();
      const taskId = req.params.taskId as string;
      const completion = await this.tasksService.completeTask(req.user.id, taskId, req.body);
      res.status(200).json(completion);
    } catch (err) {
      next(err);
    }
  };

  undoTask = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) throw new UnauthorizedError();
      const taskId = req.params.taskId as string;
      await this.tasksService.undoTask(req.user.id, taskId, req.body);
      res.status(200).json({ success: true });
    } catch (err) {
      next(err);
    }
  };

  deleteTask = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) throw new UnauthorizedError();
      const taskId = req.params.taskId as string;
      await this.tasksService.deleteTask(req.user.id, taskId);
      res.status(200).json({ deletedTaskId: taskId });
    } catch (err) {
      next(err);
    }
  };

  stopRecurrence = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) throw new UnauthorizedError();
      const taskId = req.params.taskId as string;
      await this.tasksService.stopRecurrence(req.user.id, taskId, req.body);
      res.status(200).json({ success: true });
    } catch (err) {
      next(err);
    }
  };
}
