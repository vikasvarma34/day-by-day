import express, { Express } from 'express';
import helmet from 'helmet';
import { createAuthRouter } from './auth/auth.router';
import { errorHandler } from './errors/error.middleware';
import { NotFoundError } from './errors/http-errors';
import { requestLogger } from './logging/request-logger.middleware';
import { validateContentType } from './middleware/content-type.middleware';

export function createApp(): Express {
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

  app.use('/auth', createAuthRouter());

  // Catch-all 404 for unknown routes (returns JSON instead of HTML)
  app.use((req, _res, next) => {
    next(new NotFoundError(`Route ${req.method} ${req.path} not found`));
  });

  // Global error handler
  app.use(errorHandler);

  return app;
}
