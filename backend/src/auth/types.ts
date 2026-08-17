export interface AuthUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  nickname: string | null;
}

export interface UserWithPasswordHash extends AuthUser {
  passwordHash: string;
}

export interface AuthSession {
  id: string;
  userId: string;
  tokenHash: string;
  createdAt: Date;
  expiresAt: Date;
}

export interface LoginResult {
  token: string;
  expiresAt: Date;
  user: AuthUser;
}

export interface AuthThrottle {
  userId: string;
  failedAttempts: number;
  windowStartedAt: Date;
  blockedUntil: Date | null;
  updatedAt: Date;
}
