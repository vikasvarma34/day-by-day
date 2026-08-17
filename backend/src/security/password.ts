import * as argon2 from 'argon2';

const ARGON2_OPTIONS: argon2.HashOptions & { raw?: false } = {
  type: argon2.argon2id,
  memoryCost: 19456, // 19 MiB (OWASP minimum recommendation for Argon2id)
  timeCost: 2,
  parallelism: 1,
  raw: false,
};

/**
 * Precomputed dummy Argon2id hash using exact OWASP parameters (m=19456, t=2, p=1).
 * Used for constant-time verification when an email does not exist or an account is blocked,
 * completely mitigating timing-based account enumeration attacks without computing new hashes per request.
 */
export const DUMMY_ARGON2_HASH =
  '$argon2id$v=19$m=19456,p=1,t=2$YaRampDLQPb+4Kb68Y/wcQ$gP+eClcCE4gLl+nhwoWRLhU1EqMfOOJktgDb5ZCmTk8';

/**
 * Hashes a plaintext password asynchronously using Argon2id with explicit OWASP parameters.
 * Automatically generates a unique cryptographically secure salt per hash.
 * Never logs passwords or hashes.
 */
export async function hashPassword(password: string): Promise<string> {
  if (!password) {
    throw new Error('Password must not be empty');
  }
  return argon2.hash(password, ARGON2_OPTIONS);
}

/**
 * Verifies a plaintext password against a stored Argon2id hash asynchronously.
 * Returns true if match, false if no match or invalid hash.
 * Never logs passwords or hashes.
 */
export async function verifyPassword(hash: string, password: string): Promise<boolean> {
  if (!hash || !password) {
    return false;
  }
  try {
    return await argon2.verify(hash, password);
  } catch {
    return false;
  }
}
