import { EventEmitter } from 'node:events';
import type { Server } from 'node:http';
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  checkDatabaseReadiness,
  getMigrationNames,
  StartupError,
  validateBootConfiguration,
} from './readiness';
import { startServer } from './server';
import type { StartServerOptions } from './server';

function fakeApp(): { app: StartServerOptions['app']; wasCalled: () => boolean } {
  let called = false;
  const app = {
    listen: () => {
      called = true;
      const server = new EventEmitter() as Server;
      process.nextTick(() => server.emit('listening'));
      return server;
    },
  } as unknown as StartServerOptions['app'];

  return { app, wasCalled: () => called };
}

function healthyClient(appliedMigrations: string[] = getMigrationNames()) {
  return {
    async query<T extends Record<string, unknown>>(text: string): Promise<{ rows: T[] }> {
      if (text.includes('pgmigrations')) {
        return { rows: appliedMigrations.map((name) => ({ name })) as unknown as T[] };
      }
      return { rows: [{ connected: 1 } as unknown as T] };
    },
  };
}

test('missing DATABASE_URL fails before the HTTP listener is started', async () => {
  const fake = fakeApp();

  assert.throws(
    () => validateBootConfiguration({}),
    (error: Error) => {
      assert.equal(error.message, 'Startup failed: missing required environment variable DATABASE_URL');
      return true;
    }
  );

  await assert.rejects(
    () => startServer({
      environment: {},
      app: fake.app,
      readinessCheck: async () => {},
      closeDatabase: async () => {},
      onStarted: () => {},
    }),
    StartupError
  );
  assert.equal(fake.wasCalled(), false);
});

test('database connectivity failure fails before the HTTP listener is started', async () => {
  const fake = fakeApp();
  const unavailableClient = {
    async query(): Promise<{ rows: never[] }> {
      throw new Error('connection refused');
    },
  };

  await assert.rejects(
    () => startServer({
      environment: { DATABASE_URL: 'postgresql://configured.example/daybyday' },
      app: fake.app,
      readinessCheck: () => checkDatabaseReadiness({ client: unavailableClient }),
      closeDatabase: async () => {},
      onStarted: () => {},
    }),
    (error: Error) => {
      assert.equal(error.message, 'Startup failed: database readiness check failed');
      return true;
    }
  );
  assert.equal(fake.wasCalled(), false);
});

test('missing migration state fails before the HTTP listener is started', async () => {
  const fake = fakeApp();
  const client = healthyClient([]);

  await assert.rejects(
    () => startServer({
      environment: { DATABASE_URL: 'postgresql://configured.example/daybyday' },
      app: fake.app,
      readinessCheck: () => checkDatabaseReadiness({ client }),
      closeDatabase: async () => {},
      onStarted: () => {},
    }),
    (error: Error) => {
      assert.equal(error.message, 'Startup failed: database schema is not ready');
      return true;
    }
  );
  assert.equal(fake.wasCalled(), false);
});

test('healthy compatible database allows the server to start after readiness succeeds', async () => {
  const fake = fakeApp();
  let started = false;

  const server = await startServer({
    environment: { DATABASE_URL: 'postgresql://configured.example/daybyday' },
    app: fake.app,
    readinessCheck: () => checkDatabaseReadiness({ client: healthyClient() }),
    closeDatabase: async () => {},
    onStarted: () => {
      started = true;
    },
  });

  assert.equal(fake.wasCalled(), true);
  assert.equal(started, true);
  server.removeAllListeners();
});
