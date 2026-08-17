-- Forward-only migration: Create auth_throttles table

CREATE TABLE IF NOT EXISTS auth_throttles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  window_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  blocked_until TIMESTAMPTZ NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT auth_throttles_failed_attempts_check CHECK (failed_attempts >= 0)
);