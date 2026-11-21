-- Up Migration
ALTER TABLE task_schedules ADD COLUMN reminder_minutes INTEGER[];

-- Down Migration
ALTER TABLE task_schedules DROP COLUMN IF EXISTS reminder_minutes;
