-- Preserve the client request that created a job-match aggregate so retried
-- submissions resolve to the original workflow instead of duplicating it.
ALTER TABLE job_match_tasks ADD COLUMN create_request_id VARCHAR(128) NULL;

CREATE UNIQUE INDEX uk_job_match_create_request
    ON job_match_tasks(account_id, create_request_id);
