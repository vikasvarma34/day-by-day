import { Router } from 'express';
import { createAuthMiddleware } from '../auth/auth.middleware';
import { AuthService } from '../auth/auth.service';
import { DayController } from './day/day.controller';
import { DayRepository } from './day/day.repository';

export function createPlannerRouter(
  dayRepository: DayRepository = new DayRepository(),
  authService: AuthService = new AuthService()
): Router {
  const router = Router();
  const dayController = new DayController(dayRepository);
  const authMiddleware = createAuthMiddleware(authService);

  router.get('/days/:date', authMiddleware, dayController.getDay);

  return router;
}
