import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getEnv, getNodeEnv, getLogLevel } from './env';

test('getEnv returns value when environment variable is set', () => {
  const testKey = 'TEST_ENV_VAR_SET';
  process.env[testKey] = 'test-value';
  try {
    const val = getEnv(testKey);
    assert.equal(val, 'test-value');
  } finally {
    delete process.env[testKey];
  }
});

test('getEnv throws descriptive error when environment variable is missing', () => {
  const testKey = 'TEST_ENV_VAR_MISSING';
  delete process.env[testKey];
  assert.throws(
    () => getEnv(testKey),
    (err: Error) => {
      assert.match(err.message, /Missing required environment variable: TEST_ENV_VAR_MISSING/);
      return true;
    }
  );
});

test('getEnv throws descriptive error when environment variable is empty whitespace', () => {
  const testKey = 'TEST_ENV_VAR_EMPTY';
  process.env[testKey] = '   ';
  try {
    assert.throws(
      () => getEnv(testKey),
      (err: Error) => {
        assert.match(err.message, /Missing required environment variable: TEST_ENV_VAR_EMPTY/);
        return true;
      }
    );
  } finally {
    delete process.env[testKey];
  }
});

test('getNodeEnv validates NODE_ENV and defaults to development', () => {
  const original = process.env.NODE_ENV;
  try {
    delete process.env.NODE_ENV;
    assert.equal(getNodeEnv(), 'development');

    process.env.NODE_ENV = 'test';
    assert.equal(getNodeEnv(), 'test');

    process.env.NODE_ENV = 'production';
    assert.equal(getNodeEnv(), 'production');

    process.env.NODE_ENV = 'invalid_env';
    assert.throws(() => getNodeEnv(), (err: Error) => {
      assert.match(err.message, /Invalid NODE_ENV/);
      return true;
    });
  } finally {
    process.env.NODE_ENV = original;
  }
});

test('getLogLevel provides environment defaults and validates explicit overrides', () => {
  const originalNodeEnv = process.env.NODE_ENV;
  const originalLogLevel = process.env.LOG_LEVEL;
  try {
    delete process.env.LOG_LEVEL;

    // Environment defaults when LOG_LEVEL is not specified
    process.env.NODE_ENV = 'development';
    assert.equal(getLogLevel(), 'debug');

    process.env.NODE_ENV = 'test';
    assert.equal(getLogLevel(), 'error');

    process.env.NODE_ENV = 'production';
    assert.equal(getLogLevel(), 'info');

    // Explicit override
    process.env.LOG_LEVEL = 'warn';
    assert.equal(getLogLevel(), 'warn');

    process.env.LOG_LEVEL = 'invalid_level';
    assert.throws(() => getLogLevel(), (err: Error) => {
      assert.match(err.message, /Invalid LOG_LEVEL/);
      return true;
    });
  } finally {
    process.env.NODE_ENV = originalNodeEnv;
    process.env.LOG_LEVEL = originalLogLevel;
  }
});
