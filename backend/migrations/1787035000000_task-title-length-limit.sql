-- Up Migration
ALTER TABLE tasks ADD CONSTRAINT tasks_title_length_check CHECK (length(title) <= 255);

-- Down Migration
ALTER TABLE tasks DROP CONSTRAINT tasks_title_length_check;
