import { Router } from 'express';
import { createAuthMiddleware } from '../auth/auth.middleware';
import { AuthService } from '../auth/auth.service';
import { DayController } from './day/day.controller';
import { DayService } from './day/day.service';
import { LaterController } from './later/later.controller';
import { LaterService } from './later/later.service';

export function createPlannerRouter(
  dayService: DayService = new DayService(),
  laterService: LaterService = new LaterService(),
  authService: AuthService = new AuthService()
): Router {
  const router = Router();
  const dayController = new DayController(dayService);
  const laterController = new LaterController(laterService);
  const authMiddleware = createAuthMiddleware(authService);

  router.get('/days/:date', authMiddleware, dayController.getDay);
  router.get('/later', authMiddleware, laterController.getLater);

  return router;
}
