import { LogLevel, getLogLevel } from '../config/env';

const LEVEL_SEVERITY: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
};

export interface LogRecord {
  timestamp: string;
  level: LogLevel;
  event: string;
  [key: string]: unknown;
}

export type LogWriter = (record: LogRecord) => void;

const defaultWriter: LogWriter = (record: LogRecord) => {
  const line = JSON.stringify(record);
  if (record.level === 'error') {
    console.error(line);
  } else {
    console.log(line);
  }
};

export class Logger {
  constructor(
    private readonly levelResolver: () => LogLevel = getLogLevel,
    private readonly writer: LogWriter = defaultWriter
  ) {}

  private shouldLog(level: LogLevel): boolean {
    const configuredLevel = this.levelResolver();
    return LEVEL_SEVERITY[level] >= LEVEL_SEVERITY[configuredLevel];
  }

  private write(level: LogLevel, event: string, meta: Record<string, unknown> = {}): void {
    if (!this.shouldLog(level)) {
      return;
    }

    const record: LogRecord = {
      timestamp: new Date().toISOString(),
      level,
      event,
      ...meta,
    };

    this.writer(record);
  }

  debug(event: string, meta?: Record<string, unknown>): void {
    this.write('debug', event, meta);
  }

  info(event: string, meta?: Record<string, unknown>): void {
    this.write('info', event, meta);
  }

  warn(event: string, meta?: Record<string, unknown>): void {
    this.write('warn', event, meta);
  }

  error(event: string, meta?: Record<string, unknown>): void {
    this.write('error', event, meta);
  }
}

export const logger = new Logger();
