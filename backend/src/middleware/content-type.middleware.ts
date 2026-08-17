import { Request, Response, NextFunction } from 'express';
import { UnsupportedMediaTypeError } from '../errors/http-errors';

/**
 * Validates that requests containing a request body provide an application/json Content-Type header.
 * Requests without a body (e.g. GET, HEAD, or Content-Length 0) are permitted without a Content-Type header.
 */
export function validateContentType(req: Request, _res: Response, next: NextFunction): void {
  const contentLength = req.headers['content-length'];
  const transferEncoding = req.headers['transfer-encoding'];

  const hasBody =
    (contentLength !== undefined && parseInt(contentLength, 10) > 0) ||
    transferEncoding !== undefined;

  if (hasBody) {
    const contentType = req.headers['content-type'];
    if (!contentType || !contentType.toLowerCase().includes('application/json')) {
      next(
        new UnsupportedMediaTypeError(
          'Unsupported media type: request body must have Content-Type: application/json'
        )
      );
      return;
    }
  }

  next();
}
