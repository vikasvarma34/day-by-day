-- Up Migration

ALTER TABLE users RENAME COLUMN username TO email;
ALTER TABLE users RENAME CONSTRAINT users_username_key TO users_email_key;
ALTER TABLE users DROP CONSTRAINT users_username_check;
ALTER TABLE users ADD CONSTRAINT users_email_check CHECK (
  length(email) > 0 AND
  email = lower(trim(email)) AND
  email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'
);

-- Down Migration