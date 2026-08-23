import { logger } from './logging/logger';
import { StartupError } from './startup/readiness';
import { startServer } from './startup/server';

startServer().catch((error: unknown) => {
  logger.error('startup_failed', {
    reason: error instanceof StartupError ? error.message : 'Startup failed: server could not start',
  });
  process.exitCode = 1;
});
