import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validatePlannerDate, validatePlausiblePlannerToday } from './date-validation';
import { BadRequestError } from '../../errors/http-errors';

test('validatePlannerDate: accepts valid standard calendar dates', () => {
  assert.equal(validatePlannerDate('2026-08-18'), '2026-08-18');
  assert.equal(validatePlannerDate('2026-01-01'), '2026-01-01');
  assert.equal(validatePlannerDate('2026-12-31'), '2026-12-31');
  assert.equal(validatePlannerDate('2026-04-30'), '2026-04-30');
});

test('validatePlannerDate: accepts valid leap day (Feb 29 on leap years)', () => {
  assert.equal(validatePlannerDate('2024-02-29'), '2024-02-29');
  assert.equal(validatePlannerDate('2000-02-29'), '2000-02-29');
});

test('validatePlannerDate: rejects Feb 29 on non-leap years', () => {
  assert.throws(() => validatePlannerDate('2025-02-29'), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.equal(err.code, 'BAD_REQUEST');
    assert.match(err.message, /impossible calendar date/);
    return true;
  });

  assert.throws(() => validatePlannerDate('1900-02-29'), BadRequestError);
  assert.throws(() => validatePlannerDate('2026-02-29'), BadRequestError);
});

test('validatePlannerDate: rejects malformed date strings', () => {
  const malformedInputs = [
    '2026-8-18',
    '2026-08-1',
    '26-08-18',
    '2026/08/18',
    '2026.08.18',
    'abc',
    '2026-08-18T10:00:00Z',
    '',
    '   ',
    null,
    undefined,
    12345,
  ];

  for (const input of malformedInputs) {
    assert.throws(() => validatePlannerDate(input), BadRequestError);
  }
});

test('validatePlannerDate: rejects impossible calendar dates', () => {
  const impossibleDates = [
    '2026-02-30',
    '2026-02-31',
    '2026-04-31',
    '2026-06-31',
    '2026-09-31',
    '2026-11-31',
    '2026-01-32',
    '2026-00-15',
    '2026-13-01',
    '2026-01-00',
  ];

  for (const date of impossibleDates) {
    assert.throws(() => validatePlannerDate(date), BadRequestError);
  }
});

test('validatePlannerDate: rejects Gregorian year zero (0000-01-01)', () => {
  assert.throws(() => validatePlannerDate('0000-01-01'), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.equal(err.code, 'BAD_REQUEST');
    assert.match(err.message, /year must be between 0001 and 9999/);
    return true;
  });
});

test('validatePlausiblePlannerToday: accepts UTC-1, UTC, UTC+1 relative to reference date', () => {
  const ref = new Date('2026-08-20T12:00:00Z');
  assert.equal(validatePlausiblePlannerToday('2026-08-19', ref), '2026-08-19');
  assert.equal(validatePlausiblePlannerToday('2026-08-20', ref), '2026-08-20');
  assert.equal(validatePlausiblePlannerToday('2026-08-21', ref), '2026-08-21');
});

test('validatePlausiblePlannerToday: rejects dates outside UTC ± 1 day window', () => {
  const ref = new Date('2026-08-20T12:00:00Z');
  assert.throws(() => validatePlausiblePlannerToday('2026-08-18', ref), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.equal(err.message, 'plannerToday is outside plausible calendar range');
    return true;
  });
  assert.throws(() => validatePlausiblePlannerToday('2026-08-22', ref), (err: any) => {
    assert.ok(err instanceof BadRequestError);
    assert.equal(err.message, 'plannerToday is outside plausible calendar range');
    return true;
  });
  assert.throws(() => validatePlausiblePlannerToday('1900-01-01', ref), BadRequestError);
  assert.throws(() => validatePlausiblePlannerToday('2099-01-01', ref), BadRequestError);
});

test('validatePlausiblePlannerToday: handles Year rollover boundary correctly', () => {
  const refJan1 = new Date('2026-01-01T02:00:00Z');
  assert.equal(validatePlausiblePlannerToday('2025-12-31', refJan1), '2025-12-31');
  assert.equal(validatePlausiblePlannerToday('2026-01-01', refJan1), '2026-01-01');
  assert.equal(validatePlausiblePlannerToday('2026-01-02', refJan1), '2026-01-02');
  assert.throws(() => validatePlausiblePlannerToday('2025-12-30', refJan1), BadRequestError);
  assert.throws(() => validatePlausiblePlannerToday('2026-01-03', refJan1), BadRequestError);
});

test('validatePlausiblePlannerToday: handles Leap year boundary correctly', () => {
  const refMar1 = new Date('2024-03-01T15:00:00Z');
  assert.equal(validatePlausiblePlannerToday('2024-02-29', refMar1), '2024-02-29');
  assert.equal(validatePlausiblePlannerToday('2024-03-01', refMar1), '2024-03-01');
  assert.equal(validatePlausiblePlannerToday('2024-03-02', refMar1), '2024-03-02');
  assert.throws(() => validatePlausiblePlannerToday('2024-02-28', refMar1), BadRequestError);
  assert.throws(() => validatePlausiblePlannerToday('2024-03-03', refMar1), BadRequestError);
});
