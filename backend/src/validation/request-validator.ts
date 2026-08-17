import { BadRequestError } from '../errors/http-errors';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface StringOptions {
  minLength?: number;
  maxLength?: number;
  nonBlank?: boolean;
}

export interface IntegerOptions {
  min?: number;
  max?: number;
}

/**
 * Validates that an incoming value is a non-null, non-array object.
 */
export function requireObject(value: unknown, fieldName: string = 'request body'): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new BadRequestError(`Invalid ${fieldName}: must be an object`);
  }
  return value as Record<string, unknown>;
}

/**
 * Validates that an incoming field is a string satisfying optional length/non-blank bounds.
 */
export function requireString(value: unknown, fieldName: string, options: StringOptions = {}): string {
  if (typeof value !== 'string') {
    throw new BadRequestError(`Invalid ${fieldName}: must be a string`);
  }

  if (options.nonBlank && value.trim().length === 0) {
    throw new BadRequestError(`Invalid ${fieldName}: cannot be blank`);
  }

  if (options.minLength !== undefined && value.length < options.minLength) {
    throw new BadRequestError(`Invalid ${fieldName}: minimum length is ${options.minLength}`);
  }

  if (options.maxLength !== undefined && value.length > options.maxLength) {
    throw new BadRequestError(`Invalid ${fieldName}: maximum length is ${options.maxLength}`);
  }

  return value;
}

/**
 * Validates an optional string field. Returns undefined if null/undefined.
 */
export function optionalString(value: unknown, fieldName: string, options: StringOptions = {}): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return requireString(value, fieldName, options);
}

/**
 * Validates that a string is a standard UUID.
 */
export function requireUuid(value: unknown, fieldName: string): string {
  const str = requireString(value, fieldName);
  if (!UUID_REGEX.test(str)) {
    throw new BadRequestError(`Invalid ${fieldName}: must be a valid UUID`);
  }
  return str;
}

/**
 * Validates an optional UUID field. Returns undefined if null/undefined.
 */
export function optionalUuid(value: unknown, fieldName: string): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return requireUuid(value, fieldName);
}

/**
 * Validates that an incoming field is a boolean primitive.
 */
export function requireBoolean(value: unknown, fieldName: string): boolean {
  if (typeof value !== 'boolean') {
    throw new BadRequestError(`Invalid ${fieldName}: must be a boolean`);
  }
  return value;
}

/**
 * Validates an optional boolean field. Returns undefined if null/undefined.
 */
export function optionalBoolean(value: unknown, fieldName: string): boolean | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return requireBoolean(value, fieldName);
}

/**
 * Validates that an incoming field is an integer with optional min/max boundaries.
 */
export function requireInteger(value: unknown, fieldName: string, options: IntegerOptions = {}): number {
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    throw new BadRequestError(`Invalid ${fieldName}: must be an integer`);
  }

  if (options.min !== undefined && value < options.min) {
    throw new BadRequestError(`Invalid ${fieldName}: minimum value is ${options.min}`);
  }

  if (options.max !== undefined && value > options.max) {
    throw new BadRequestError(`Invalid ${fieldName}: maximum value is ${options.max}`);
  }

  return value;
}

/**
 * Validates an optional integer field. Returns undefined if null/undefined.
 */
export function optionalInteger(value: unknown, fieldName: string, options: IntegerOptions = {}): number | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return requireInteger(value, fieldName, options);
}
