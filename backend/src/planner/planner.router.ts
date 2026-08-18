import { Router } from 'express';
import { createAuthMiddleware } from '../auth/auth.middleware';
import { AuthService } from '../auth/auth.service';
import { DayController } from './day/day.controller';
import { DayService } from './day/day.service';
import { LaterController } from './later/later.controller';
import { LaterService } from './later/later.service';
import { HistoryController } from './history/history.controller';
import { HistoryService } from './history/history.service';
import { RefreshController } from './refresh/refresh.controller';
import { RefreshService } from './refresh/refresh.service';

export function createPlannerRouter(
  dayService: DayService = new DayService(),
  laterService: LaterService = new LaterService(),
  historyService: HistoryService = new HistoryService(),
  refreshService: RefreshService = new RefreshService(),
  authService: AuthService = new AuthService()
): Router {
  const router = Router();
  const dayController = new DayController(dayService);
  const laterController = new LaterController(laterService);
  const historyController = new HistoryController(historyService);
  const refreshController = new RefreshController(refreshService);
  const authMiddleware = createAuthMiddleware(authService);

  router.get('/days/:date', authMiddleware, dayController.getDay);
  router.get('/later', authMiddleware, laterController.getLater);
  router.get('/history', authMiddleware, historyController.getHistory);
  router.get('/refresh', authMiddleware, refreshController.getRefresh);

  return router;
}
