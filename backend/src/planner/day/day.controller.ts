import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../../auth/auth.middleware';
import { UnauthorizedError } from '../../errors/http-errors';
import { validatePlannerDate } from '../domain/date-validation';
import { DayService } from './day.service';

export class DayController {
  constructor(private readonly dayService: DayService = new DayService()) {}

  getDay = async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const requestedDate = validatePlannerDate(req.params.date, 'date');
      const response = await this.dayService.getDay(req.user.id, requestedDate);

      res.status(200).json(response);
    } catch (err) {
      next(err);
    }
  };
}
