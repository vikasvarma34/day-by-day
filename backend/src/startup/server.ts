import type { Express } from 'express';
import type { Server } from 'node:http';
import { createApp } from '../app';
import { closeStartupDatabase, checkDatabaseReadiness, StartupError, validateBootConfiguration } from './readiness';

export interface StartServerOptions {
  port?: string | number;
  environment?: NodeJS.ProcessEnv;
  app?: Pick<Express, 'listen'>;
  readinessCheck?: () => Promise<void>;
  closeDatabase?: () => Promise<void>;
  onStarted?: (port: string | number) => void;
}

export async function startServer(options: StartServerOptions = {}): Promise<Server> {
  const port = options.port ?? process.env.PORT ?? 3000;
  const app = options.app ?? createApp();
  const readinessCheck = options.readinessCheck ?? checkDatabaseReadiness;
  const closeDatabase = options.closeDatabase ?? closeStartupDatabase;
  const onStarted = options.onStarted ?? ((startedPort) => {
    console.log(`Server running on port ${startedPort}`);
  });

  validateBootConfiguration(options.environment ?? process.env);

  try {
    await readinessCheck();
  } catch (error) {
    await closeDatabase().catch(() => {});
    if (error instanceof StartupError) {
      throw error;
    }
    throw new StartupError('Startup failed: database readiness check failed');
  }

  return new Promise<Server>((resolve, reject) => {
    let server: Server;
    try {
      server = app.listen(port);
    } catch {
      void closeDatabase().catch(() => {});
      reject(new StartupError('Startup failed: HTTP server could not listen'));
      return;
    }

    server.once('error', () => {
      void closeDatabase().catch(() => {});
      reject(new StartupError('Startup failed: HTTP server could not listen'));
    });
    server.once('listening', () => {
      onStarted(port);
      resolve(server);
    });
  });
}
