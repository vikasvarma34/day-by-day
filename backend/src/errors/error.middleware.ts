import { Request, Response, NextFunction, ErrorRequestHandler } from 'express';
import { HttpError } from './http-errors';
import { AuthenticationError } from '../auth/auth.service';
import { logger } from '../logging/logger';

/**
 * Global error-handling middleware for Express.
 * Formats all errors into a consistent JSON shape:
 * {
 *   "error": {
 *     "code": "BAD_REQUEST" | "UNAUTHORIZED" | "NOT_FOUND" | "PAYLOAD_TOO_LARGE" | "UNSUPPORTED_MEDIA_TYPE" | "INTERNAL_ERROR",
 *     "message": "..."
 *   }
 * }
 * Intercepts body-parser 413 and malformed JSON 400 errors safely.
 * Logs unhandled 500 errors safely with requestId correlation without leaking stack traces or SQL details.
 */
export const errorHandler: ErrorRequestHandler = (
  err: any,
  req: Request,
  res: Response,
  _next: NextFunction
): void => {
  if (err instanceof HttpError) {
    res.status(err.status).json({
      error: {
        code: err.code,
        message: err.message,
      },
    });
    return;
  }

  if (err instanceof AuthenticationError) {
    res.status(401).json({
      error: {
        code: 'UNAUTHORIZED',
        message: err.message,
      },
    });
    return;
  }

  // Handle Express body-parser entity too large (413)
  if (err.type === 'entity.too.large' || err.status === 413) {
    res.status(413).json({
      error: {
        code: 'PAYLOAD_TOO_LARGE',
        message: 'Request payload exceeds the maximum allowed size of 64 KB',
      },
    });
    return;
  }

  // Handle Express body-parser JSON syntax errors (400)
  const isSyntaxError = err instanceof SyntaxError && (err as any).status === 400 && 'body' in (err as any);
  if (err.type === 'entity.parse.failed' || isSyntaxError) {
    res.status(400).json({
      error: {
        code: 'BAD_REQUEST',
        message: 'Malformed JSON payload in request body',
      },
    });
    return;
  }

  // Safe correlated error log for unhandled 500 exceptions
  logger.error('unhandled_server_error', {
    requestId: req.requestId,
    method: req.method,
    path: req.path,
    errorType: err?.name || 'Error',
  });

  // Safe fallback response for unhandled internal exceptions
  res.status(500).json({
    error: {
      code: 'INTERNAL_ERROR',
      message: 'Internal server error',
    },
  });
};
