import { Request, Response, NextFunction } from 'express';
import { AuthService } from './auth.service';
import { AuthUser } from './types';
import { UnauthorizedError } from '../errors/http-errors';

export interface AuthenticatedRequest extends Request {
  user?: AuthUser;
}

/**
 * Express middleware that validates Bearer session tokens against PostgreSQL.
 * Attaches the resolved AuthUser to req.user for downstream handlers.
 * Rejects missing, malformed, invalid, or expired tokens with HTTP 401 Unauthorized.
 */
export function createAuthMiddleware(authService: AuthService = new AuthService()) {
  return async (req: AuthenticatedRequest, _res: Response, next: NextFunction): Promise<void> => {
    const authHeader = req.headers.authorization;
    if (!authHeader || typeof authHeader !== 'string') {
      next(new UnauthorizedError('Missing Authorization header'));
      return;
    }

    const parts = authHeader.trim().split(' ');
    if (parts.length !== 2 || parts[0] !== 'Bearer' || !parts[1].trim()) {
      next(new UnauthorizedError('Malformed Authorization header'));
      return;
    }

    const token = parts[1].trim();
    try {
      const user = await authService.authenticateSession(token);
      if (!user) {
        next(new UnauthorizedError('Invalid or expired session'));
        return;
      }

      req.user = user;
      next();
    } catch (err) {
      next(err);
    }
  };
}
