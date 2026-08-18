import { getEnv } from '../src/config/env';
import { AuthRepository } from '../src/auth/auth.repository';
import { createUserAccount } from '../src/auth/user-creation';
import { normalizeEmail } from '../src/security/validation';
import { closePool } from '../src/db/pool';

export async function seedDevelopmentUser(): Promise<void> {
  const email = getEnv('TEST_USER_EMAIL');
  const password = getEnv('TEST_USER_PASSWORD');

  const normalizedEmail = normalizeEmail(email);
  const repository = new AuthRepository();

  try {
    // 1. Idempotency check: does the persistent development user already exist?
    const existing = await repository.findUserByEmail(normalizedEmail);
    if (existing) {
      console.log(`✓ Development user already exists: ${existing.email} (ID: ${existing.id}). No changes made.`);
      return;
    }

    // 2. Create user with standard validation, Argon2id hashing, and defaults
    const user = await createUserAccount({
      email,
      password,
      firstName: 'Dev',
      lastName: 'Tester',
      nickname: 'Dev',
    });

    console.log(`✓ Development user seeded successfully: ${user.email} (ID: ${user.id}).`);
  } catch (error: any) {
    console.error('Failed to seed development user:', error.message || error);
    process.exitCode = 1;
  } finally {
    await closePool();
  }
}

if (require.main === module) {
  seedDevelopmentUser();
}
