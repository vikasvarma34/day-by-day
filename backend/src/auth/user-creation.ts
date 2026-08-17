import { randomUUID } from 'node:crypto';
import { getPool } from '../db/pool';
import { AuthUser } from './types';
import { normalizeEmail, validateEmail, validatePassword } from '../security/validation';
import { hashPassword } from '../security/password';

export interface CreateUserInput {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  nickname?: string | null;
}

export class UserCreationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UserCreationError';
  }
}

/**
 * Validates and creates a new user account in PostgreSQL.
 * Normalizes email, validates password length, hashes password with Argon2id,
 * generates a UUID, and enforces non-blank first/last names.
 */
export async function createUserAccount(input: CreateUserInput): Promise<AuthUser> {
  // 1. Validate email
  if (!validateEmail(input.email)) {
    throw new UserCreationError('Invalid email format');
  }

  // 2. Validate password
  if (!validatePassword(input.password)) {
    throw new UserCreationError('Password must be between 15 and 128 characters');
  }

  // 3. Validate first and last names
  if (!input.firstName || typeof input.firstName !== 'string' || input.firstName.trim().length === 0) {
    throw new UserCreationError('First name is required and cannot be blank');
  }

  if (!input.lastName || typeof input.lastName !== 'string' || input.lastName.trim().length === 0) {
    throw new UserCreationError('Last name is required and cannot be blank');
  }

  // 4. Normalize fields
  const normalizedEmail = normalizeEmail(input.email);
  const trimmedFirstName = input.firstName.trim();
  const trimmedLastName = input.lastName.trim();
  const normalizedNickname =
    input.nickname && typeof input.nickname === 'string' && input.nickname.trim().length > 0
      ? input.nickname.trim()
      : null;

  // 5. Hash password with Argon2id
  const passwordHash = await hashPassword(input.password);
  const userId = randomUUID();

  // 6. Insert into database using parameterized query
  const pool = getPool();
  try {
    const result = await pool.query(
      `INSERT INTO users (id, email, password_hash, first_name, last_name, nickname)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING id, email, first_name, last_name, nickname`,
      [userId, normalizedEmail, passwordHash, trimmedFirstName, trimmedLastName, normalizedNickname]
    );

    const row = result.rows[0];
    return {
      id: row.id,
      email: row.email,
      firstName: row.first_name,
      lastName: row.last_name,
      nickname: row.nickname,
    };
  } catch (error: any) {
    if (error.code === '23505') {
      throw new UserCreationError(`A user with email "${normalizedEmail}" already exists`);
    }
    throw error;
  }
}
