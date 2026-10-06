ALTER TABLE async_tasks ADD COLUMN progress_percent INT NOT NULL DEFAULT 0;
ALTER TABLE async_tasks ADD COLUMN checkpoint_code VARCHAR(64) NULL;
ALTER TABLE async_tasks ADD COLUMN error_code VARCHAR(128) NULL;

