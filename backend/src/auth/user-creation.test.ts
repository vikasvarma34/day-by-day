import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createUserAccount, UserCreationError } from './user-creation';

test('createUserAccount validation throws on invalid inputs before querying DB', async () => {
  // 1. Invalid email
  await assert.rejects(
    () =>
      createUserAccount({
        email: 'invalid-email',
        password: 'ValidPassword12345!',
        firstName: 'John',
        lastName: 'Doe',
      }),
    (err: Error) => {
      assert.equal(err.name, 'UserCreationError');
      assert.match(err.message, /Invalid email/);
      return true;
    }
  );

  // 2. Short password (14 chars)
  await assert.rejects(
    () =>
      createUserAccount({
        email: 'valid@example.com',
        password: '12345678901234',
        firstName: 'John',
        lastName: 'Doe',
      }),
    (err: Error) => {
      assert.equal(err.name, 'UserCreationError');
      assert.match(err.message, /Password must be between 15 and 128 characters/);
      return true;
    }
  );

  // 3. Long password (129 chars)
  await assert.rejects(
    () =>
      createUserAccount({
        email: 'valid@example.com',
        password: 'a'.repeat(129),
        firstName: 'John',
        lastName: 'Doe',
      }),
    (err: Error) => {
      assert.equal(err.name, 'UserCreationError');
      assert.match(err.message, /Password must be between 15 and 128 characters/);
      return true;
    }
  );

  // 4. Blank first name
  await assert.rejects(
    () =>
      createUserAccount({
        email: 'valid@example.com',
        password: 'ValidPassword12345!',
        firstName: '   ',
        lastName: 'Doe',
      }),
    (err: Error) => {
      assert.equal(err.name, 'UserCreationError');
      assert.match(err.message, /First name is required/);
      return true;
    }
  );

  // 5. Blank last name
  await assert.rejects(
    () =>
      createUserAccount({
        email: 'valid@example.com',
        password: 'ValidPassword12345!',
        firstName: 'John',
        lastName: '',
      }),
    (err: Error) => {
      assert.equal(err.name, 'UserCreationError');
      assert.match(err.message, /Last name is required/);
      return true;
    }
  );
});
