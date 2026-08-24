/// <reference path="./types/express.d.ts" />

import express, { Express } from 'express';
import helmet from 'helmet';
import { createAuthRouter } from './auth/auth.router';
import { createPlannerRouter } from './planner/planner.router';
import { createTasksRouter } from './planner/tasks/tasks.router';
import { getPool } from './db/pool';
import { errorHandler } from './errors/error.middleware';
import { NotFoundError } from './errors/http-errors';
import { requestLogger } from './logging/request-logger.middleware';
import { validateContentType } from './middleware/content-type.middleware';

export interface AppOptions {
  dbClient?: {
    query: (text: string, values?: readonly unknown[]) => Promise<unknown>;
  };
}

export function createApp(options: AppOptions = {}): Express {
  const app = express();

  // Disable Express signature
  app.disable('x-powered-by');

  // Security headers
  app.use(helmet());

  // Request correlation & completion logger (mounted first)
  app.use(requestLogger);

  // Content type validation for requests with body
  app.use(validateContentType);

  // JSON body parsing with strict 64 KB limit
  app.use(express.json({ limit: '64kb' }));

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.get('/ready', async (_req, res) => {
    try {
      const client = options.dbClient ?? getPool();
      await client.query('SELECT 1');
      res.json({ status: 'ready' });
    } catch {
      res.status(503).json({ status: 'unavailable' });
    }
  });

  app.use('/auth', createAuthRouter());
  app.use('/planner', createPlannerRouter());
  app.use('/tasks', createTasksRouter());

  // Catch-all 404 for unknown routes (returns JSON instead of HTML)
  app.use((req, _res, next) => {
    next(new NotFoundError(`Route ${req.method} ${req.path} not found`));
  });

  // Global error handler
  app.use(errorHandler);

  return app;
}

export default createApp();
