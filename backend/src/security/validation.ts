const EMAIL_REGEX = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MIN_PASSWORD_LENGTH = 15;
const MAX_PASSWORD_LENGTH = 128;

/**
 * Trims surrounding whitespace and converts email to lowercase.
 */
export function normalizeEmail(email: string): string {
  if (typeof email !== 'string') {
    return '';
  }
  return email.trim().toLowerCase();
}

/**
 * Validates practical email pattern matching database constraints without full RFC overhead.
 */
export function validateEmail(email: string): boolean {
  if (!email || typeof email !== 'string') {
    return false;
  }
  const normalized = normalizeEmail(email);
  return EMAIL_REGEX.test(normalized);
}

/**
 * Validates password input: minimum 15 chars, maximum 128 chars.
 * Does not alter or trim the password string.
 * Allows arbitrary characters, spaces, and symbols without character-class mandates.
 */
export function validatePassword(password: string): boolean {
  if (!password || typeof password !== 'string') {
    return false;
  }
  return password.length >= MIN_PASSWORD_LENGTH && password.length <= MAX_PASSWORD_LENGTH;
}
