export type NodeEnv = 'development' | 'test' | 'production';
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

const VALID_NODE_ENVS: ReadonlySet<string> = new Set(['development', 'test', 'production']);
const VALID_LOG_LEVELS: ReadonlySet<string> = new Set(['debug', 'info', 'warn', 'error']);

export function getEnv(name: string): string {
  const value = process.env[name];
  if (!value || value.trim() === '') {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function getNodeEnv(): NodeEnv {
  const env = process.env.NODE_ENV?.trim();
  if (!env) {
    return 'development';
  }

  if (!VALID_NODE_ENVS.has(env)) {
    throw new Error(`Invalid NODE_ENV "${env}": must be "development", "test", or "production"`);
  }

  return env as NodeEnv;
}

export function getLogLevel(): LogLevel {
  const level = process.env.LOG_LEVEL?.trim().toLowerCase();
  if (level) {
    if (!VALID_LOG_LEVELS.has(level)) {
      throw new Error(`Invalid LOG_LEVEL "${level}": must be "debug", "info", "warn", or "error"`);
    }
    return level as LogLevel;
  }

  // Default log levels based on NODE_ENV
  const nodeEnv = getNodeEnv();
  switch (nodeEnv) {
    case 'test':
      return 'error';
    case 'production':
      return 'info';
    case 'development':
    default:
      return 'debug';
  }
}
