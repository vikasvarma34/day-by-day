import { AuthUser } from '../auth/types';

declare global {
  namespace Express {
    interface Request {
      requestId?: string;
      user?: AuthUser;
    }
  }
}

export {};
