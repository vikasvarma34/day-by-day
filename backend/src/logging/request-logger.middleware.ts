import { Request, Response, NextFunction } from 'express';
import { randomUUID } from 'node:crypto';
import { Logger, logger as defaultLogger } from './logger';

/**
 * Express middleware that:
 * 1. Generates a unique UUID requestId for each incoming request.
 * 2. Attaches requestId to req.requestId and sets X-Request-Id header.
 * 3. Emits exactly one structured http_request_completed log record when response finishes.
 * 4. Never logs request bodies, query strings, headers, tokens, or credentials.
 */
export function createRequestLogger(logger: Logger = defaultLogger) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const requestId = randomUUID();
    req.requestId = requestId;
    res.setHeader('X-Request-Id', requestId);

    const startNs = process.hrtime.bigint();

    res.on('finish', () => {
      const durationMs = Math.round(Number(process.hrtime.bigint() - startNs) / 1_000_000);
      const status = res.statusCode;

      const payload = {
        requestId,
        method: req.method,
        path: req.path,
        status,
        durationMs,
      };

      if (status >= 500) {
        logger.error('http_request_completed', payload);
      } else if (status >= 400) {
        logger.warn('http_request_completed', payload);
      } else {
        logger.info('http_request_completed', payload);
      }
    });

    next();
  };
}

export const requestLogger = createRequestLogger();
