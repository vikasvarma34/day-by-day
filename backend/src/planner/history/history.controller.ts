import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../../auth/auth.middleware';
import { UnauthorizedError } from '../../errors/http-errors';
import { validatePlannerDate } from '../domain/date-validation';
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

      const plannerToday = validatePlannerDate(req.query.plannerToday, 'plannerToday');
      const qParam = typeof req.query.q === 'string' ? req.query.q.trim() : undefined;
      const search = qParam && qParam.length > 0 ? qParam : undefined;

      const response = await this.historyService.getHistory(req.user.id, plannerToday, search);

      res.status(200).json(response);
    } catch (err) {
      next(err);
    }
  };
}
