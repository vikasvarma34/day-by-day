import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test('production start script loads local environment when .env exists', () => {
  const packageJson = JSON.parse(
    readFileSync(resolve(__dirname, '../../package.json'), 'utf8')
  ) as { scripts?: { start?: string } };

  assert.equal(
    packageJson.scripts?.start,
    'node --env-file-if-exists=.env dist/server.js'
  );
});
