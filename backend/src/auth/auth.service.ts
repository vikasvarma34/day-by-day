import { AuthRepository } from './auth.repository';
import { AuthUser, LoginResult } from './types';
import { normalizeEmail, validateEmail, validatePassword } from '../security/validation';
import { verifyPassword, DUMMY_ARGON2_HASH } from '../security/password';
import {
  generateSessionToken,
  hashSessionToken,
  calculateSessionExpiry,
} from '../security/session';

export class AuthenticationError extends Error {
  constructor(message: string = 'Invalid email or password') {
    super(message);
    this.name = 'AuthenticationError';
  }
}

export class AuthService {
  constructor(private readonly authRepository: AuthRepository = new AuthRepository()) {}

  /**
   * Authenticates user credentials with durable failed-login throttling and enumeration mitigation:
   * - Validates email and password shapes.
   * - Performs constant Argon2id verification using a precomputed dummy hash on unknown emails or blocked accounts.
   * - Blocks user internally for 15 minutes upon reaching 5 failed password attempts within 15 minutes.
   * - When internally blocked, skips verifying against the real password hash, preventing brute-force while returning the same generic 401 response.
   * - Never creates throttle rows for unknown emails.
   * - Resets throttle on successful login.
   * - Never logs passwords, raw tokens, or hashes.
   */
  async login(email: string, password: string, now: Date = new Date()): Promise<LoginResult> {
    // 1. Input validation & normalization
    if (!validateEmail(email) || !validatePassword(password)) {
      throw new AuthenticationError();
    }

    const normalizedEmail = normalizeEmail(email);

    // 2. Fetch user by email
    const userWithHash = await this.authRepository.findUserByEmail(normalizedEmail);

    // 3. Unknown email path: execute dummy Argon2id verification to match timing, never create DB rows
    if (!userWithHash) {
      await verifyPassword(DUMMY_ARGON2_HASH, password);
      throw new AuthenticationError();
    }

    // 4. Check if account is currently blocked
    const activeThrottle = await this.authRepository.findActiveThrottle(userWithHash.id, now);
    if (activeThrottle && activeThrottle.blockedUntil && activeThrottle.blockedUntil.getTime() > now.getTime()) {
      // Account is internally blocked: do not test real hash, perform dummy verification and fail generically
      await verifyPassword(DUMMY_ARGON2_HASH, password);
      throw new AuthenticationError();
    }

    // 5. Verify real Argon2id password hash
    const isPasswordValid = await verifyPassword(userWithHash.passwordHash, password);
    if (!isPasswordValid) {
      await this.authRepository.recordFailedLogin(userWithHash.id, now);
      throw new AuthenticationError();
    }

    // 6. Clear / reset throttle on successful password verification
    await this.authRepository.resetThrottle(userWithHash.id, now);

    // 7. Generate 32-byte opaque token and 30-day expiry
    const rawToken = generateSessionToken();
    const tokenHash = hashSessionToken(rawToken);
    const expiresAt = calculateSessionExpiry(now);

    // 8. Store session in database (hashed token only)
    await this.authRepository.createSession(userWithHash.id, tokenHash, expiresAt);

    // 9. Return raw token and user profile
    const authUser: AuthUser = {
      id: userWithHash.id,
      email: userWithHash.email,
      firstName: userWithHash.firstName,
      lastName: userWithHash.lastName,
      nickname: userWithHash.nickname,
    };

    return {
      token: rawToken,
      expiresAt,
      user: authUser,
    };
  }

  /**
   * Authenticates an incoming raw session token.
   * Resolves the user if the session exists and has not expired.
   * Returns null on invalid, expired, or nonexistent tokens.
   */
  async authenticateSession(rawToken: string): Promise<AuthUser | null> {
    if (!rawToken || typeof rawToken !== 'string' || rawToken.trim() === '') {
      return null;
    }

    try {
      const tokenHash = hashSessionToken(rawToken);
      return await this.authRepository.findValidSessionAndUser(tokenHash);
    } catch {
      return null;
    }
  }

  /**
   * Invalidates a session by deleting its matching row in auth_sessions.
   * Fully idempotent: invalid or already-expired tokens do not throw.
   */
  async logout(rawToken: string): Promise<void> {
    if (!rawToken || typeof rawToken !== 'string' || rawToken.trim() === '') {
      return;
    }

    try {
      const tokenHash = hashSessionToken(rawToken);
      await this.authRepository.deleteSessionByTokenHash(tokenHash);
    } catch {
      // Idempotent error handling
    }
  }
}
