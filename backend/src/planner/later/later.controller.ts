import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../../auth/auth.middleware';
import { UnauthorizedError } from '../../errors/http-errors';
import { LaterService } from './later.service';

export class LaterController {
  constructor(private readonly laterService: LaterService = new LaterService()) {}

  getLater = async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const response = await this.laterService.getLater(req.user.id);

      res.status(200).json(response);
    } catch (err) {
      next(err);
    }
  };
}
