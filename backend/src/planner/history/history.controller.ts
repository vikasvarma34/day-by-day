import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../../auth/auth.middleware';
import { BadRequestError, UnauthorizedError } from '../../errors/http-errors';
import { validatePlausiblePlannerToday } from '../domain/date-validation';
import { HistoryService } from './history.service';

export class HistoryController {
  constructor(private readonly historyService: HistoryService = new HistoryService()) {}

  getHistory = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const plannerToday = validatePlausiblePlannerToday(req.query.plannerToday);

      let search: string | undefined = undefined;
      if (req.query.q !== undefined) {
        if (typeof req.query.q !== 'string') {
          throw new BadRequestError('Invalid search query: must be a string');
        }
        if (req.query.q.length > 200) {
          throw new BadRequestError('Invalid search query: max 200 characters');
        }
        const trimmed = req.query.q.trim();
        if (trimmed.length > 0) {
          search = trimmed;
        }
      }

      const response = await this.historyService.getHistory(req.user.id, plannerToday, search);

      res.status(200).json(response);
    } catch (err) {
      next(err);
    }
  };
}
