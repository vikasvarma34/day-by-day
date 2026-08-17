import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Logger, LogRecord } from './logger';
import { LogLevel } from '../config/env';

test('Logger filters log emissions according to configured LOG_LEVEL threshold', () => {
  const records: LogRecord[] = [];
  let currentLevel: LogLevel = 'debug';

  const testLogger = new Logger(
    () => currentLevel,
    (rec) => records.push(rec)
  );

  // 1. debug level emits all
  currentLevel = 'debug';
  records.length = 0;
  testLogger.debug('evt_debug');
  testLogger.info('evt_info');
  testLogger.warn('evt_warn');
  testLogger.error('evt_error');
  assert.equal(records.length, 4);

  // 2. info level suppresses debug
  currentLevel = 'info';
  records.length = 0;
  testLogger.debug('evt_debug');
  testLogger.info('evt_info');
  testLogger.warn('evt_warn');
  testLogger.error('evt_error');
  assert.equal(records.length, 3);
  assert.deepEqual(records.map((r) => r.event), ['evt_info', 'evt_warn', 'evt_error']);

  // 3. warn level suppresses debug & info
  currentLevel = 'warn';
  records.length = 0;
  testLogger.debug('evt_debug');
  testLogger.info('evt_info');
  testLogger.warn('evt_warn');
  testLogger.error('evt_error');
  assert.equal(records.length, 2);
  assert.deepEqual(records.map((r) => r.event), ['evt_warn', 'evt_error']);

  // 4. error level suppresses debug, info, & warn
  currentLevel = 'error';
  records.length = 0;
  testLogger.debug('evt_debug');
  testLogger.info('evt_info');
  testLogger.warn('evt_warn');
  testLogger.error('evt_error');
  assert.equal(records.length, 1);
  assert.equal(records[0].event, 'evt_error');
});

test('Logger emits valid structured record with timestamp, level, event, and meta', () => {
  const records: LogRecord[] = [];
  const testLogger = new Logger(
    () => 'debug',
    (rec) => records.push(rec)
  );

  testLogger.info('user_action', { userId: '123', count: 5 });

  assert.equal(records.length, 1);
  const rec = records[0];
  assert.equal(rec.level, 'info');
  assert.equal(rec.event, 'user_action');
  assert.equal(rec.userId, '123');
  assert.equal(rec.count, 5);
  assert.ok(rec.timestamp);
  assert.ok(!isNaN(new Date(rec.timestamp).getTime()));
});
