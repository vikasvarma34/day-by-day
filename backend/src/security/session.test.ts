import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  generateSessionToken,
  hashSessionToken,
  calculateSessionExpiry,
  SESSION_DURATION_MS,
} from './session';

test('generateSessionToken produces unique tokens with 32 bytes of entropy', () => {
  const token1 = generateSessionToken();
  const token2 = generateSessionToken();

  assert.notEqual(token1, token2, 'Consecutive tokens should be distinct');

  // Base64url decoded length must be 32 bytes
  const buffer1 = Buffer.from(token1, 'base64url');
  const buffer2 = Buffer.from(token2, 'base64url');
  assert.equal(buffer1.length, 32, 'Raw token buffer must contain 32 bytes');
  assert.equal(buffer2.length, 32, 'Raw token buffer must contain 32 bytes');
});

test('hashSessionToken deterministically hashes tokens with SHA-256', () => {
  const token1 = 'test_token_alpha_12345';
  const token2 = 'test_token_beta_67890';

  const hash1A = hashSessionToken(token1);
  const hash1B = hashSessionToken(token1);
  const hash2 = hashSessionToken(token2);

  assert.equal(hash1A, hash1B, 'Hashing identical token must produce identical hash');
  assert.notEqual(hash1A, hash2, 'Hashing different tokens must produce distinct hashes');
  assert.equal(hash1A.length, 64, 'SHA-256 hex digest should be 64 characters');
  assert.match(hash1A, /^[0-9a-f]{64}$/, 'Hash should be lowercase hex characters');
});

test('hashSessionToken throws on empty token input', () => {
  assert.throws(
    () => hashSessionToken(''),
    (err: Error) => {
      assert.equal(err.message, 'Session token must not be empty');
      return true;
    }
  );
});

test('calculateSessionExpiry calculates expiry exactly 30 days from supplied creation time', () => {
  const creationTime = new Date('2026-08-17T12:00:00.000Z');
  const expiry = calculateSessionExpiry(creationTime);

  const diffMs = expiry.getTime() - creationTime.getTime();
  assert.equal(diffMs, SESSION_DURATION_MS);
  assert.equal(diffMs, 30 * 24 * 60 * 60 * 1000);
  assert.equal(expiry.toISOString(), '2026-09-16T12:00:00.000Z');
});

test('calculateSessionExpiry default argument calculates expiry within 5 seconds of now + 30 days', () => {
  const before = Date.now();
  const expiry = calculateSessionExpiry();
  const after = Date.now();

  const expectedExpiryBefore = before + SESSION_DURATION_MS;
  const expectedExpiryAfter = after + SESSION_DURATION_MS;

  assert.ok(expiry.getTime() >= expectedExpiryBefore);
  assert.ok(expiry.getTime() <= expectedExpiryAfter);
});
