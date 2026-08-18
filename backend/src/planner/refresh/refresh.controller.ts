import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../../auth/auth.middleware';
import { UnauthorizedError } from '../../errors/http-errors';
import { RefreshService } from './refresh.service';

export class RefreshController {
  constructor(private readonly refreshService: RefreshService = new RefreshService()) {}

  getRefresh = async (
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError();
      }

      const snapshot = await this.refreshService.getSnapshot(req.user.id);

      res.status(200);
      res.setHeader('Content-Type', 'application/json; charset=utf-8');

      // Stream JSON chunk-by-chunk to avoid buffering an unbounded single object in memory
      res.write('{"tasks":[');
      for (let i = 0; i < snapshot.tasks.length; i++) {
        if (i > 0) res.write(',');
        res.write(JSON.stringify(snapshot.tasks[i]));
      }

      res.write('],"schedules":[');
      for (let i = 0; i < snapshot.schedules.length; i++) {
        if (i > 0) res.write(',');
        res.write(JSON.stringify(snapshot.schedules[i]));
      }

      res.write('],"completions":[');
      for (let i = 0; i < snapshot.completions.length; i++) {
        if (i > 0) res.write(',');
        res.write(JSON.stringify(snapshot.completions[i]));
      }

      res.write(']}');
      res.end();
    } catch (err) {
      next(err);
    }
  };
}
