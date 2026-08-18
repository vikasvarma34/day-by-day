-- Up Migration
ALTER TABLE task_schedules DROP CONSTRAINT task_schedules_interval_check;
ALTER TABLE task_schedules ADD CONSTRAINT task_schedules_interval_check CHECK (
  schedule_type != 'INTERVAL_DAYS' OR (
    interval_days IS NOT NULL AND
    interval_days >= 1 AND
    interval_anchor_date IS NOT NULL AND
    interval_anchor_date <= start_date AND
    weekdays_mask IS NULL
  )
);

ALTER TABLE task_schedules DROP CONSTRAINT task_schedules_weekdays_check;
ALTER TABLE task_schedules ADD CONSTRAINT task_schedules_weekdays_check CHECK (
  schedule_type != 'WEEKDAYS' OR (
    weekdays_mask IS NOT NULL AND
    weekdays_mask BETWEEN 1 AND 127 AND
    interval_days IS NULL AND
    interval_anchor_date IS NULL
  )
);

ALTER TABLE task_completions DROP CONSTRAINT task_completions_schedule_fk;
ALTER TABLE task_completions ADD CONSTRAINT task_completions_schedule_fk
  FOREIGN KEY (schedule_id, task_id) 
  REFERENCES task_schedules(id, task_id);

-- Down Migration
ALTER TABLE task_completions DROP CONSTRAINT task_completions_schedule_fk;
ALTER TABLE task_completions ADD CONSTRAINT task_completions_schedule_fk
  FOREIGN KEY (schedule_id, task_id) 
  REFERENCES task_schedules(id, task_id) ON DELETE RESTRICT;

ALTER TABLE task_schedules DROP CONSTRAINT task_schedules_weekdays_check;
ALTER TABLE task_schedules ADD CONSTRAINT task_schedules_weekdays_check CHECK (
  schedule_type != 'WEEKDAYS' OR (
    weekdays_mask BETWEEN 1 AND 127 AND
    interval_days IS NULL AND
    interval_anchor_date IS NULL
  )
);

ALTER TABLE task_schedules DROP CONSTRAINT task_schedules_interval_check;
ALTER TABLE task_schedules ADD CONSTRAINT task_schedules_interval_check CHECK (
  schedule_type != 'INTERVAL_DAYS' OR (
    interval_days >= 1 AND
    interval_anchor_date IS NOT NULL AND
    interval_anchor_date <= start_date AND
    weekdays_mask IS NULL
  )
);