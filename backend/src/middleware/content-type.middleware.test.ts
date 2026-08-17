import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateContentType } from './content-type.middleware';
import { UnsupportedMediaTypeError } from '../errors/http-errors';

test('validateContentType allows bodyless requests without Content-Type', () => {
  let nextCalled = false;
  let nextErr: any = null;

  const req: any = {
    headers: {},
  };

  validateContentType(req, {} as any, (err?: any) => {
    nextCalled = true;
    nextErr = err;
  });

  assert.ok(nextCalled);
  assert.equal(nextErr, undefined);
});

test('validateContentType allows application/json Content-Type when body is present', () => {
  let nextCalled = false;
  let nextErr: any = null;

  const req: any = {
    headers: {
      'content-length': '25',
      'content-type': 'application/json; charset=utf-8',
    },
  };

  validateContentType(req, {} as any, (err?: any) => {
    nextCalled = true;
    nextErr = err;
  });

  assert.ok(nextCalled);
  assert.equal(nextErr, undefined);
});

test('validateContentType rejects non-JSON Content-Type when body is present with 415', () => {
  let nextCalled = false;
  let nextErr: any = null;

  const req: any = {
    headers: {
      'content-length': '25',
      'content-type': 'text/plain',
    },
  };

  validateContentType(req, {} as any, (err?: any) => {
    nextCalled = true;
    nextErr = err;
  });

  assert.ok(nextCalled);
  assert.ok(nextErr instanceof UnsupportedMediaTypeError);
  assert.equal(nextErr.status, 415);
  assert.equal(nextErr.code, 'UNSUPPORTED_MEDIA_TYPE');
});
