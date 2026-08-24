declare namespace Express {
  interface Request {
    requestId?: string;
    user?: import('../auth/types').AuthUser;
  }
}
