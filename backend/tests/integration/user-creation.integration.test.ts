import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createUserAccount, UserCreationError } from '../../src/auth/user-creation';
import { verifyPassword } from '../../src/security/password';
import { getPool, closePool } from '../../src/db/pool';
import { deleteTestUserById, generateAutomatedTestEmail } from './auth-test-fixtures';

test('User Creation Integration Suite', async (t) => {
  const pool = getPool();
  const createdUserIds: string[] = [];

  t.after(async () => {
    for (const id of createdUserIds) {
      await deleteTestUserById(id);
    }
    await closePool();
  });

  function generateUniqueEmail(label: string): string {
    return generateAutomatedTestEmail(`create.${label}`);
  }

  await t.test('valid user creation data succeeds and stores Argon2 hash in database', async () => {
    const rawEmail = generateUniqueEmail('valid');
    const rawPassword = 'StrongPassword12345!';

    const user = await createUserAccount({
      email: rawEmail,
      password: rawPassword,
      firstName: 'Alice',
      lastName: 'Smith',
      nickname: 'Ali',
    });

    createdUserIds.push(user.id);

    assert.ok(user.id, 'User should have a generated UUID');
    assert.equal(user.email, rawEmail);
    assert.equal(user.firstName, 'Alice');
    assert.equal(user.lastName, 'Smith');
    assert.equal(user.nickname, 'Ali');

    // Query database to verify Argon2id hash storage (never plaintext)
    const dbRow = await pool.query('SELECT password_hash FROM users WHERE id = $1', [user.id]);
    assert.equal(dbRow.rows.length, 1);
    const storedHash = dbRow.rows[0].password_hash;

    assert.notEqual(storedHash, rawPassword, 'Database must never store plaintext password');
    assert.ok(storedHash.startsWith('$argon2id$'), 'Password hash must be Argon2id');
    assert.equal(await verifyPassword(storedHash, rawPassword), true, 'Stored hash must verify with password');
  });

  await t.test('email normalization works for uppercase and outer whitespace', async () => {
    const baseEmail = generateUniqueEmail('normalized');
    const messyEmail = `   ${baseEmail.toUpperCase()}   `;

    const user = await createUserAccount({
      email: messyEmail,
      password: 'StrongPassword12345!',
      firstName: 'Bob',
      lastName: 'Jones',
    });

    createdUserIds.push(user.id);
    assert.equal(user.email, baseEmail.toLowerCase());
  });

  await t.test('blank nickname becomes NULL in database', async () => {
    const email = generateUniqueEmail('nonick');

    const user = await createUserAccount({
      email,
      password: 'StrongPassword12345!',
      firstName: 'Charlie',
      lastName: 'Brown',
      nickname: '   ', // Blank whitespace nickname
    });

    createdUserIds.push(user.id);
    assert.equal(user.nickname, null);

    const dbRow = await pool.query('SELECT nickname FROM users WHERE id = $1', [user.id]);
    assert.equal(dbRow.rows[0].nickname, null);
  });

  await t.test('password length boundaries (14 fails, 15 succeeds, 128 succeeds, 129 fails)', async () => {
    const baseEmail = generateUniqueEmail('pwdlen');

    // 14 characters - fails
    await assert.rejects(
      () =>
        createUserAccount({
          email: `${baseEmail}.14`,
          password: '12345678901234',
          firstName: 'Dave',
          lastName: 'Miller',
        }),
      UserCreationError
    );

    // 15 characters - succeeds
    const user15 = await createUserAccount({
      email: `${baseEmail}.15`,
      password: '123456789012345',
      firstName: 'Dave',
      lastName: 'Miller',
    });
    createdUserIds.push(user15.id);
    assert.ok(user15.id);

    // 128 characters - succeeds
    const user128 = await createUserAccount({
      email: `${baseEmail}.128`,
      password: 'a'.repeat(128),
      firstName: 'Dave',
      lastName: 'Miller',
    });
    createdUserIds.push(user128.id);
    assert.ok(user128.id);

    // 129 characters - fails
    await assert.rejects(
      () =>
        createUserAccount({
          email: `${baseEmail}.129`,
          password: 'a'.repeat(129),
          firstName: 'Dave',
          lastName: 'Miller',
        }),
      UserCreationError
    );
  });

  await t.test('strong passwords with symbols, unicode, and spaces are accepted', async () => {
    const email = generateUniqueEmail('symbols');
    const complexPassword = 'correct horse battery staple &!@#$%^&*() 🔒';

    const user = await createUserAccount({
      email,
      password: complexPassword,
      firstName: 'Eve',
      lastName: 'Davis',
    });

    createdUserIds.push(user.id);
    assert.ok(user.id);
  });

  await t.test('duplicate email fails safely without creating second user', async () => {
    const email = generateUniqueEmail('duplicate');

    const user1 = await createUserAccount({
      email,
      password: 'StrongPassword12345!',
      firstName: 'Frank',
      lastName: 'Wilson',
    });
    createdUserIds.push(user1.id);

    // Attempt to create user with identical email
    await assert.rejects(
      () =>
        createUserAccount({
          email: email.toUpperCase(), // Same normalized email
          password: 'AnotherPassword12345!',
          firstName: 'Frank',
          lastName: 'Wilson',
        }),
      (err: Error) => {
        assert.equal(err.name, 'UserCreationError');
        assert.match(err.message, /already exists/);
        return true;
      }
    );
  });
});
