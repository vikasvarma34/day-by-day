-- Up Migration

CREATE TABLE tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  title TEXT NOT NULL,
  note TEXT,
  is_important BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT tasks_title_check CHECK (length(trim(title)) > 0),
  CONSTRAINT tasks_note_check CHECK (length(note) <= 500)
);

CREATE INDEX tasks_user_id_idx ON tasks(user_id);

CREATE TABLE task_schedules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  schedule_type TEXT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  scheduled_time TIME,
  interval_days INTEGER,
  interval_anchor_date DATE,
  weekdays_mask INTEGER,
  reminder_minutes_before INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT task_schedules_schedule_type_check CHECK (schedule_type IN ('ONCE', 'INTERVAL_DAYS', 'WEEKDAYS')),
  CONSTRAINT task_schedules_end_date_check CHECK (end_date IS NULL OR end_date >= start_date),
  
  CONSTRAINT task_schedules_once_check CHECK (
    schedule_type != 'ONCE' OR (
      end_date IS NOT NULL AND
      start_date = end_date AND
      interval_days IS NULL AND
      interval_anchor_date IS NULL AND
      weekdays_mask IS NULL
    )
  ),
  
  CONSTRAINT task_schedules_interval_check CHECK (
    schedule_type != 'INTERVAL_DAYS' OR (
      interval_days >= 1 AND
      interval_anchor_date IS NOT NULL AND
      interval_anchor_date <= start_date AND
      weekdays_mask IS NULL
    )
  ),
  
  CONSTRAINT task_schedules_weekdays_check CHECK (
    schedule_type != 'WEEKDAYS' OR (
      weekdays_mask BETWEEN 1 AND 127 AND
      interval_days IS NULL AND
      interval_anchor_date IS NULL
    )
  ),
  
  CONSTRAINT task_schedules_reminder_check CHECK (
    reminder_minutes_before IS NULL OR 
    reminder_minutes_before IN (0, 5, 10, 15, 30, 60, 1440, 2880)
  ),
  
  CONSTRAINT task_schedules_reminder_time_check CHECK (
    reminder_minutes_before IS NULL OR scheduled_time IS NOT NULL
  ),
  
  CONSTRAINT task_schedules_unique_task_start UNIQUE (task_id, start_date),
  
  CONSTRAINT task_schedules_id_task_id_unique UNIQUE (id, task_id)
);

CREATE UNIQUE INDEX task_schedules_single_open_ended_idx 
  ON task_schedules(task_id) 
  WHERE end_date IS NULL;

CREATE TABLE task_completions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  schedule_id UUID,
  scheduled_date DATE,
  completed_date DATE NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL,
  title_snapshot TEXT,
  is_important_snapshot BOOLEAN NOT NULL,
  
  CONSTRAINT task_completions_schedule_sync_check CHECK (
    (schedule_id IS NULL AND scheduled_date IS NULL) OR 
    (schedule_id IS NOT NULL AND scheduled_date IS NOT NULL)
  ),
  
  CONSTRAINT task_completions_schedule_fk 
    FOREIGN KEY (schedule_id, task_id) 
    REFERENCES task_schedules(id, task_id) 
    ON DELETE RESTRICT,
    
  CONSTRAINT task_completions_unique_occurrence UNIQUE NULLS NOT DISTINCT (task_id, scheduled_date)
);

-- Down Migration

DROP TABLE task_completions;
DROP TABLE task_schedules;
DROP TABLE tasks;