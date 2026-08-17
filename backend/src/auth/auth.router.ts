import { Router } from 'express';
import { AuthController } from './auth.controller';
import { createAuthMiddleware } from './auth.middleware';
import { AuthService } from './auth.service';

export function createAuthRouter(authService: AuthService = new AuthService()): Router {
  const router = Router();
  const controller = new AuthController(authService);
  const authMiddleware = createAuthMiddleware(authService);

  router.post('/login', controller.login);
  router.post('/logout', controller.logout);
  router.get('/me', authMiddleware, controller.getMe);

  return router;
}
