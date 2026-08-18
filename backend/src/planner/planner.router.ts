import { Router } from 'express';
import { createAuthMiddleware } from '../auth/auth.middleware';
import { AuthService } from '../auth/auth.service';
import { PlannerController } from './planner.controller';
import { PlannerRepository } from './planner.repository';

export function createPlannerRouter(
  plannerRepository: PlannerRepository = new PlannerRepository(),
  authService: AuthService = new AuthService()
): Router {
  const router = Router();
  const controller = new PlannerController(plannerRepository);
  const authMiddleware = createAuthMiddleware(authService);

  router.get('/days/:date', authMiddleware, controller.getDay);

  return router;
}
