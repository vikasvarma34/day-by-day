-- Up Migration
ALTER TABLE tasks DROP CONSTRAINT tasks_note_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_note_check CHECK (length(note) <= 1500);

-- Down Migration
ALTER TABLE tasks DROP CONSTRAINT tasks_note_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_note_check CHECK (length(note) <= 500);

