import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  BadRequestError,
  UnauthorizedError,
  NotFoundError,
  PayloadTooLargeError,
  UnsupportedMediaTypeError,
  InternalServerError,
} from './http-errors';
import { errorHandler } from './error.middleware';
import { AuthenticationError } from '../auth/auth.service';

test('HttpError classes construct with appropriate status and machine-readable codes', () => {
  const badReq = new BadRequestError('Malformed input');
  assert.equal(badReq.status, 400);
  assert.equal(badReq.code, 'BAD_REQUEST');
  assert.equal(badReq.message, 'Malformed input');

  const unauth = new UnauthorizedError('Auth required');
  assert.equal(unauth.status, 401);
  assert.equal(unauth.code, 'UNAUTHORIZED');
  assert.equal(unauth.message, 'Auth required');

  const notFound = new NotFoundError('Route not found');
  assert.equal(notFound.status, 404);
  assert.equal(notFound.code, 'NOT_FOUND');
  assert.equal(notFound.message, 'Route not found');

  const tooLarge = new PayloadTooLargeError('Payload exceeds 64 KB');
  assert.equal(tooLarge.status, 413);
  assert.equal(tooLarge.code, 'PAYLOAD_TOO_LARGE');
  assert.equal(tooLarge.message, 'Payload exceeds 64 KB');

  const unsupp = new UnsupportedMediaTypeError('Requires application/json');
  assert.equal(unsupp.status, 415);
  assert.equal(unsupp.code, 'UNSUPPORTED_MEDIA_TYPE');
  assert.equal(unsupp.message, 'Requires application/json');

  const internal = new InternalServerError('Server error');
  assert.equal(internal.status, 500);
  assert.equal(internal.code, 'INTERNAL_ERROR');
  assert.equal(internal.message, 'Server error');
});

test('errorHandler formats HttpError correctly', () => {
  let statusCode = 0;
  let responseBody: any = null;

  const mockRes: any = {
    status(code: number) {
      statusCode = code;
      return this;
    },
    json(body: any) {
      responseBody = body;
      return this;
    },
  };

  errorHandler(new BadRequestError('Bad body'), {} as any, mockRes, () => {});

  assert.equal(statusCode, 400);
  assert.deepEqual(responseBody, {
    error: {
      code: 'BAD_REQUEST',
      message: 'Bad body',
    },
  });
});

test('errorHandler formats body-parser entity.too.large as 413 PAYLOAD_TOO_LARGE', () => {
  let statusCode = 0;
  let responseBody: any = null;

  const mockRes: any = {
    status(code: number) {
      statusCode = code;
      return this;
    },
    json(body: any) {
      responseBody = body;
      return this;
    },
  };

  const tooLargeErr: any = new Error('request entity too large');
  tooLargeErr.type = 'entity.too.large';
  tooLargeErr.status = 413;

  errorHandler(tooLargeErr, {} as any, mockRes, () => {});

  assert.equal(statusCode, 413);
  assert.equal(responseBody.error.code, 'PAYLOAD_TOO_LARGE');
});

test('errorHandler formats body-parser entity.parse.failed as 400 BAD_REQUEST', () => {
  let statusCode = 0;
  let responseBody: any = null;

  const mockRes: any = {
    status(code: number) {
      statusCode = code;
      return this;
    },
    json(body: any) {
      responseBody = body;
      return this;
    },
  };

  const parseErr: any = new SyntaxError('Unexpected token in JSON');
  parseErr.type = 'entity.parse.failed';
  parseErr.status = 400;
  parseErr.body = '{"invalid"';

  errorHandler(parseErr, {} as any, mockRes, () => {});

  assert.equal(statusCode, 400);
  assert.equal(responseBody.error.code, 'BAD_REQUEST');
});

test('errorHandler formats AuthenticationError as UNAUTHORIZED', () => {
  let statusCode = 0;
  let responseBody: any = null;

  const mockRes: any = {
    status(code: number) {
      statusCode = code;
      return this;
    },
    json(body: any) {
      responseBody = body;
      return this;
    },
  };

  errorHandler(new AuthenticationError('Invalid credentials'), {} as any, mockRes, () => {});

  assert.equal(statusCode, 401);
  assert.deepEqual(responseBody, {
    error: {
      code: 'UNAUTHORIZED',
      message: 'Invalid credentials',
    },
  });
});

test('errorHandler catches unexpected internal errors without leaking details', () => {
  let statusCode = 0;
  let responseBody: any = null;

  const mockRes: any = {
    status(code: number) {
      statusCode = code;
      return this;
    },
    json(body: any) {
      responseBody = body;
      return this;
    },
  };

  const unexpectedError = new Error('SELECT * FROM secret_table: Connection failed at 10.0.0.1:5432');
  errorHandler(unexpectedError, {} as any, mockRes, () => {});

  assert.equal(statusCode, 500);
  assert.deepEqual(responseBody, {
    error: {
      code: 'INTERNAL_ERROR',
      message: 'Internal server error',
    },
  });
});
