import { BadRequestError } from '../errors/http-errors';

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
