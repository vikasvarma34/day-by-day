import { randomBytes, createHash } from 'node:crypto';

export const SESSION_DURATION_DAYS = 30;
export const SESSION_DURATION_MS = SESSION_DURATION_DAYS * 24 * 60 * 60 * 1000;

/**
 * Generates an opaque client-facing session token containing 32 cryptographically secure random bytes
 * encoded as base64url. Contains zero user/state information.
 */
export function generateSessionToken(): string {
  return randomBytes(32).toString('base64url');
}

/**
 * Hashes a raw session token using SHA-256 for persistent database storage.
 * Never logs raw tokens or hashes.
 */
export function hashSessionToken(token: string): string {
  if (!token) {
    throw new Error('Session token must not be empty');
  }
  return createHash('sha256').update(token).digest('hex');
}

/**
 * Calculates session expiry date, fixed at exactly 30 days from creation time.
 */
export function calculateSessionExpiry(createdAt: Date = new Date()): Date {
  return new Date(createdAt.getTime() + SESSION_DURATION_MS);
}
