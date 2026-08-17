import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  requireObject,
  requireString,
  optionalString,
  requireUuid,
  optionalUuid,
  requireBoolean,
  optionalBoolean,
  requireInteger,
  optionalInteger,
} from './request-validator';
import { BadRequestError } from '../errors/http-errors';

test('requireObject validates objects and rejects non-objects / arrays', () => {
  assert.deepEqual(requireObject({ key: 'value' }), { key: 'value' });

  assert.throws(() => requireObject(null, 'body'), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.equal(err.code, 'BAD_REQUEST');
    assert.match(err.message, /must be an object/);
    return true;
  });

  assert.throws(() => requireObject([1, 2, 3], 'body'), BadRequestError);
  assert.throws(() => requireObject('string', 'body'), BadRequestError);
  assert.throws(() => requireObject(123, 'body'), BadRequestError);
});

test('requireString and optionalString validate strings, bounds, and nonBlank', () => {
  assert.equal(requireString('hello', 'title'), 'hello');
  assert.equal(requireString('hello', 'title', { minLength: 3, maxLength: 10, nonBlank: true }), 'hello');

  // Wrong type
  assert.throws(() => requireString(123, 'title'), BadRequestError);
  assert.throws(() => requireString(null, 'title'), BadRequestError);

  // Blank check
  assert.throws(() => requireString('   ', 'title', { nonBlank: true }), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.match(err.message, /cannot be blank/);
    return true;
  });

  // Length bounds
  assert.throws(() => requireString('ab', 'title', { minLength: 3 }), BadRequestError);
  assert.throws(() => requireString('abcdef', 'title', { maxLength: 5 }), BadRequestError);

  // Optional string
  assert.equal(optionalString(undefined, 'desc'), undefined);
  assert.equal(optionalString(null, 'desc'), undefined);
  assert.equal(optionalString('some description', 'desc'), 'some description');
  assert.throws(() => optionalString(123, 'desc'), BadRequestError);
});

test('requireUuid and optionalUuid validate UUID format', () => {
  const validUuid = '51fe424e-9dd6-4d77-92e5-e558b31b7282';
  assert.equal(requireUuid(validUuid, 'id'), validUuid);
  assert.equal(optionalUuid(validUuid, 'id'), validUuid);
  assert.equal(optionalUuid(undefined, 'id'), undefined);

  assert.throws(() => requireUuid('invalid-uuid-123', 'id'), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.match(err.message, /must be a valid UUID/);
    return true;
  });

  assert.throws(() => requireUuid(12345, 'id'), BadRequestError);
});

test('requireBoolean and optionalBoolean validate booleans', () => {
  assert.equal(requireBoolean(true, 'isComplete'), true);
  assert.equal(requireBoolean(false, 'isComplete'), false);
  assert.equal(optionalBoolean(undefined, 'isComplete'), undefined);
  assert.equal(optionalBoolean(true, 'isComplete'), true);

  assert.throws(() => requireBoolean('true', 'isComplete'), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.match(err.message, /must be a boolean/);
    return true;
  });
  assert.throws(() => requireBoolean(1, 'isComplete'), BadRequestError);
});

test('requireInteger and optionalInteger validate integers and ranges', () => {
  assert.equal(requireInteger(42, 'count'), 42);
  assert.equal(requireInteger(0, 'count', { min: 0, max: 100 }), 0);
  assert.equal(requireInteger(100, 'count', { min: 0, max: 100 }), 100);
  assert.equal(optionalInteger(undefined, 'count'), undefined);

  // Decimals / non-integers
  assert.throws(() => requireInteger(3.14, 'count'), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.match(err.message, /must be an integer/);
    return true;
  });
  assert.throws(() => requireInteger('42', 'count'), BadRequestError);

  // Range boundaries
  assert.throws(() => requireInteger(-1, 'count', { min: 0 }), BadRequestError);
  assert.throws(() => requireInteger(101, 'count', { max: 100 }), BadRequestError);
});
