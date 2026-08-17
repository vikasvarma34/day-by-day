import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword } from './password';

test('hashPassword hashes password and verifyPassword returns true for matching password', async () => {
  const plainPassword = 'CorrectHorseBatteryStaple123!';
  const hash = await hashPassword(plainPassword);

  assert.ok(hash.startsWith('$argon2id$'), 'Hash should be an Argon2id formatted string');

  const isValid = await verifyPassword(hash, plainPassword);
  assert.equal(isValid, true, 'Plaintext password should verify successfully against its hash');
});

test('verifyPassword returns false for wrong password', async () => {
  const plainPassword = 'CorrectHorseBatteryStaple123!';
  const wrongPassword = 'WrongPassword456!';
  const hash = await hashPassword(plainPassword);

  const isValid = await verifyPassword(hash, wrongPassword);
  assert.equal(isValid, false, 'Wrong password should fail verification');
});

test('hashing the same password twice produces different stored hashes due to unique salt', async () => {
  const plainPassword = 'SamePasswordToHashTwice';
  const hash1 = await hashPassword(plainPassword);
  const hash2 = await hashPassword(plainPassword);

  assert.notEqual(hash1, hash2, 'Two hashes of the same password should differ due to unique salts');
});

test('hashPassword throws when password is empty', async () => {
  await assert.rejects(
    async () => {
      await hashPassword('');
    },
    (err: Error) => {
      assert.equal(err.message, 'Password must not be empty');
      return true;
    }
  );
});

test('verifyPassword returns false for malformed hash or empty inputs safely', async () => {
  assert.equal(await verifyPassword('not-a-valid-argon-hash', 'password12345'), false);
  assert.equal(await verifyPassword('', 'password12345'), false);
  assert.equal(await verifyPassword('$argon2id$v=19$m=19456,t=2,p=1$invalid$hash', ''), false);
});
