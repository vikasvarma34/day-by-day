import { Request, Response, NextFunction } from 'express';
import { AuthService } from './auth.service';
import { AuthenticatedRequest } from './auth.middleware';
import { UnauthorizedError } from '../errors/http-errors';
import { requireObject, requireString } from '../validation/request-validator';

export class AuthController {
  constructor(private readonly authService: AuthService = new AuthService()) {}

  /**
   * POST /auth/login
   * Authenticates user credentials and returns a 30-day session token.
   */
  login = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const body = requireObject(req.body);
      const email = requireString(body.email, 'email');
      const password = requireString(body.password, 'password');

      const loginResult = await this.authService.login(email, password);
      res.status(200).json({
        token: loginResult.token,
        expiresAt: loginResult.expiresAt.toISOString(),
        user: loginResult.user,
      });
    } catch (error) {
      next(error);
    }
  };

  /**
   * POST /auth/logout
   * Invalidates the provided Bearer session token.
   */
  logout = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
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
      await this.authService.logout(token);
      res.status(204).send();
    } catch (error) {
      next(error);
    }
  };

  /**
   * GET /auth/me
   * Returns current authenticated user profile.
   */
  getMe = async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    if (!req.user) {
      next(new UnauthorizedError('Authentication required'));
      return;
    }

    res.status(200).json({
      user: {
        id: req.user.id,
        email: req.user.email,
        firstName: req.user.firstName,
        lastName: req.user.lastName,
        nickname: req.user.nickname,
      },
    });
  };
}
