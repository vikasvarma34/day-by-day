import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { createRequestLogger } from './request-logger.middleware';
import { Logger, LogRecord } from './logger';

test('requestLogger attaches requestId, sets X-Request-Id, and logs on finish', () => {
  const records: LogRecord[] = [];
  const testLogger = new Logger(
    () => 'debug',
    (rec) => records.push(rec)
  );

  const middleware = createRequestLogger(testLogger);

  const req: any = {
    method: 'POST',
    path: '/auth/login',
  };

  const headers: Record<string, string> = {};
  const res: any = new EventEmitter();
  res.setHeader = (k: string, v: string) => {
    headers[k] = v;
  };
  res.statusCode = 200;

  let nextCalled = false;
  middleware(req, res, () => {
    nextCalled = true;
  });

  assert.ok(nextCalled);
  assert.ok(req.requestId);
  assert.equal(headers['X-Request-Id'], req.requestId);
  assert.equal(records.length, 0); // Not logged until finish

  // Emit finish
  res.emit('finish');

  assert.equal(records.length, 1);
  const log = records[0];
  assert.equal(log.level, 'info');
  assert.equal(log.event, 'http_request_completed');
  assert.equal(log.requestId, req.requestId);
  assert.equal(log.method, 'POST');
  assert.equal(log.path, '/auth/login');
  assert.equal(log.status, 200);
  assert.ok(typeof log.durationMs === 'number');

  // Verify no sensitive fields are present
  assert.equal(log.body, undefined);
  assert.equal(log.headers, undefined);
  assert.equal(log.password, undefined);
  assert.equal(log.token, undefined);
});

test('requestLogger maps 4xx to warn and 5xx to error', () => {
  const records: LogRecord[] = [];
  const testLogger = new Logger(
    () => 'debug',
    (rec) => records.push(rec)
  );

  const middleware = createRequestLogger(testLogger);

  // 4xx -> warn
  const req401: any = { method: 'GET', path: '/auth/me' };
  const res401: any = new EventEmitter();
  res401.setHeader = () => {};
  res401.statusCode = 401;
  middleware(req401, res401, () => {});
  res401.emit('finish');
  assert.equal(records[0].level, 'warn');
  assert.equal(records[0].status, 401);

  // 5xx -> error
  const req500: any = { method: 'GET', path: '/health' };
  const res500: any = new EventEmitter();
  res500.setHeader = () => {};
  res500.statusCode = 500;
  middleware(req500, res500, () => {});
  res500.emit('finish');
  assert.equal(records[1].level, 'error');
  assert.equal(records[1].status, 500);
});
