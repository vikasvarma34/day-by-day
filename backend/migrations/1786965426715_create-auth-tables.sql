-- Up Migration

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  nickname TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT users_username_check CHECK (length(username) > 0 AND username = lower(trim(username))),
  CONSTRAINT users_first_name_check CHECK (length(trim(first_name)) > 0),
  CONSTRAINT users_last_name_check CHECK (length(trim(last_name)) > 0),
  CONSTRAINT users_nickname_check CHECK (nickname IS NULL OR length(trim(nickname)) > 0)
);

CREATE TABLE auth_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT auth_sessions_expires_at_check CHECK (expires_at > created_at)
);

-- Down Migration