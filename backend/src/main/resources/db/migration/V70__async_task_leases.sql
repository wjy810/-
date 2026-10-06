-- Task leases let several app or worker instances share the async_tasks queue: a claim records
-- who runs the task and until when; an expired lease means the worker died and the task can be
-- claimed again. The index serves the claim query (task_type + status, oldest first).
ALTER TABLE async_tasks ADD COLUMN lease_owner VARCHAR(96) NULL;
ALTER TABLE async_tasks ADD COLUMN lease_expires_at DATETIME(3) NULL;
CREATE INDEX idx_async_tasks_claim ON async_tasks (task_type, status, created_at);
