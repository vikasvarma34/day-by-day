import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizeEmail, validateEmail, validatePassword } from './validation';

test('normalizeEmail trims whitespace and lowercases email', () => {
  assert.equal(normalizeEmail('  User@Example.COM  '), 'user@example.com');
  assert.equal(normalizeEmail('JOHN.DOE@GMAIL.COM'), 'john.doe@gmail.com');
});

test('validateEmail accepts valid email formats', () => {
  assert.equal(validateEmail('user@example.com'), true);
  assert.equal(validateEmail('  alice.smith+tag@sub.domain.org  '), true);
  assert.equal(validateEmail('test_123@domain.co.in'), true);
});

test('validateEmail rejects invalid emails', () => {
  assert.equal(validateEmail(''), false);
  assert.equal(validateEmail('notanemail'), false);
  assert.equal(validateEmail('missingdomain@'), false);
  assert.equal(validateEmail('@missinguser.com'), false);
  assert.equal(validateEmail('user@nodotdomain'), false);
  assert.equal(validateEmail('user @example.com'), false);
  assert.equal(validateEmail('user@domain .com'), false);
});

test('validatePassword enforces length boundaries (15 - 128 chars)', () => {
  // 14 characters - too short
  assert.equal(validatePassword('12345678901234'), false);

  // 15 characters - minimum valid
  assert.equal(validatePassword('123456789012345'), true);

  // 128 characters - maximum valid
  const exact128 = 'a'.repeat(128);
  assert.equal(validatePassword(exact128), true);

  // 129 characters - too long
  const tooLong129 = 'a'.repeat(129);
  assert.equal(validatePassword(tooLong129), false);
});

test('validatePassword accepts symbols, unicode, and internal spaces without trimming', () => {
  const symbolPassword = 'correct horse battery staple &!@#$%^&*()';
  assert.equal(validatePassword(symbolPassword), true);

  const unicodePassword = '🔒 secure-passphrase-with-emoji 🎉';
  assert.equal(validatePassword(unicodePassword), true);

  // Password with leading/trailing spaces preserves raw character count
  const spacedPassword = '   fifteen chars   ';
  assert.equal(validatePassword(spacedPassword), true);
});
