import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { createApp } from './app';

test('GET /health returns 200 OK with status ok', async (t) => {
  const app = createApp();
  const server = app.listen(0);
  t.after(() => server.close());

  const address = server.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}/health`;

  const response = await fetch(url);
  assert.equal(response.status, 200);

  const data = await response.json();
  assert.deepEqual(data, { status: 'ok' });
});

test('GET /ready returns 200 OK with status ready when database is available', async (t) => {
  const fakeDb = {
    query: async (text: string) => {
      assert.equal(text, 'SELECT 1');
      return { rows: [{ '?column?': 1 }] };
    },
  };

  const app = createApp({ dbClient: fakeDb });
  const server = app.listen(0);
  t.after(() => server.close());

  const address = server.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}/ready`;

  const response = await fetch(url);
  assert.equal(response.status, 200);

  const data = await response.json();
  assert.deepEqual(data, { status: 'ready' });
});

test('GET /ready returns 503 Service Unavailable with status unavailable when database is unavailable', async (t) => {
  const fakeDb = {
    query: async () => {
      throw new Error('Connection terminated unexpectedly: postgresql://secret_user:secret_pass@db.internal:5432/db');
    },
  };

  const app = createApp({ dbClient: fakeDb });
  const server = app.listen(0);
  t.after(() => server.close());

  const address = server.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}/ready`;

  const response = await fetch(url);
  assert.equal(response.status, 503);

  const data = await response.json();
  assert.deepEqual(data, { status: 'unavailable' });
  assert.equal(JSON.stringify(data).includes('secret'), false);
});
