import { BadRequestError } from '../../errors/http-errors';

const DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

function getDaysInMonth(year: number, month: number): number {
  switch (month) {
    case 1:
    case 3:
    case 5:
    case 7:
    case 8:
    case 10:
    case 12:
      return 31;
    case 4:
    case 6:
    case 9:
    case 11:
      return 30;
    case 2:
      return isLeapYear(year) ? 29 : 28;
    default:
      return 0;
  }
}

/**
 * Validates that an incoming planner date parameter is an exact YYYY-MM-DD Gregorian calendar date.
 * Does not perform any timezone conversion or clock-dependent checks.
 */
export function validatePlannerDate(dateStr: unknown, paramName: string = 'date'): string {
  if (typeof dateStr !== 'string' || !DATE_REGEX.test(dateStr)) {
    throw new BadRequestError(`Invalid ${paramName}: must be in YYYY-MM-DD format`);
  }

  const [yStr, mStr, dStr] = dateStr.split('-');
  const year = Number(yStr);
  const month = Number(mStr);
  const day = Number(dStr);

  if (year < 1) {
    throw new BadRequestError(`Invalid ${paramName}: year must be between 0001 and 9999`);
  }

  if (month < 1 || month > 12) {
    throw new BadRequestError(`Invalid ${paramName}: month must be between 01 and 12`);
  }

  const daysInMonth = getDaysInMonth(year, month);
  if (day < 1 || day > daysInMonth) {
    throw new BadRequestError(`Invalid ${paramName}: impossible calendar date`);
  }

  return dateStr;
}

/**
 * Formats a Date object to YYYY-MM-DD using its UTC calendar components.
 */
export function formatUtcDate(date: Date): string {
  const y = date.getUTCFullYear().toString().padStart(4, '0');
  const m = (date.getUTCMonth() + 1).toString().padStart(2, '0');
  const d = date.getUTCDate().toString().padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Validates that an incoming plannerToday parameter is:
 * 1. A valid Gregorian calendar date in YYYY-MM-DD format.
 * 2. Plausibly the user's current date: within ±1 calendar day of the server's current UTC date.
 *
 * This provides a security boundary preventing arbitrary spoofed dates (e.g. 1900 or 2099)
 * while accommodating all legitimate global time zones (UTC-12 to UTC+14).
 *
 * @param dateStr The date string claiming to be the client's current date.
 * @param referenceNow Optional reference Date (defaults to server current time) for deterministic testing.
 */
export function validatePlausiblePlannerToday(dateStr: unknown, referenceNow: Date = new Date()): string {
  const validated = validatePlannerDate(dateStr, 'plannerToday');

  const refUtcMs = Date.UTC(referenceNow.getUTCFullYear(), referenceNow.getUTCMonth(), referenceNow.getUTCDate());
  const oneDayMs = 24 * 60 * 60 * 1000;

  const minUtcDate = formatUtcDate(new Date(refUtcMs - oneDayMs));
  const maxUtcDate = formatUtcDate(new Date(refUtcMs + oneDayMs));

  if (validated < minUtcDate || validated > maxUtcDate) {
    throw new BadRequestError('plannerToday is outside plausible calendar range');
  }

  return validated;
}
