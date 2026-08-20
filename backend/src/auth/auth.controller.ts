import { Request, Response, NextFunction } from 'express';
import { AuthService } from './auth.service';
import { AuthenticatedRequest } from './auth.middleware';
import { BadRequestError, UnauthorizedError } from '../errors/http-errors';
import { requireObject, requireString } from '../validation/request-validator';
import { UpdateProfileInput } from './types';

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

  /**
   * POST /auth/change-password
   * Authenticated password change endpoint.
   * Atomically updates password and revokes all active sessions for the user.
   */
  changePassword = async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError('Authentication required');
      }

      const body = requireObject(req.body);
      const currentPassword = requireString(body.currentPassword, 'currentPassword');
      const newPassword = requireString(body.newPassword, 'newPassword');

      await this.authService.changePassword(req.user.id, currentPassword, newPassword);

      res.status(200).json({ success: true });
    } catch (error) {
      next(error);
    }
  };

  /**
   * PATCH /auth/profile
   * Authenticated endpoint to update profile fields (firstName, lastName, nickname)
   * for the currently authenticated user.
   */
  updateProfile = async (req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        throw new UnauthorizedError('Authentication required');
      }

      const body = requireObject(req.body);
      const updates: UpdateProfileInput = {};

      if ('firstName' in body) {
        const firstNameStr = requireString(body.firstName, 'firstName', { nonBlank: true, maxLength: 100 });
        updates.firstName = firstNameStr.trim();
      }

      if ('lastName' in body) {
        const lastNameStr = requireString(body.lastName, 'lastName', { nonBlank: true, maxLength: 100 });
        updates.lastName = lastNameStr.trim();
      }

      if ('nickname' in body) {
        if (body.nickname === null) {
          updates.nickname = null;
        } else if (typeof body.nickname === 'string') {
          const nicknameStr = requireString(body.nickname, 'nickname', { maxLength: 100 });
          const trimmed = nicknameStr.trim();
          updates.nickname = trimmed.length > 0 ? trimmed : null;
        } else {
          throw new BadRequestError('Invalid nickname: must be a string or null');
        }
      }

      const updatedUser = await this.authService.updateProfile(req.user.id, updates);

      res.status(200).json({
        user: {
          id: updatedUser.id,
          email: updatedUser.email,
          firstName: updatedUser.firstName,
          lastName: updatedUser.lastName,
          nickname: updatedUser.nickname,
        },
      });
    } catch (error) {
      next(error);
    }
  };
}
