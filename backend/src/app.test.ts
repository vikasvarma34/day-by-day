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
