ALTER TABLE ai_resume_change_sets ADD COLUMN source_type VARCHAR(48) NULL;
ALTER TABLE ai_resume_change_sets ADD COLUMN source_id CHAR(36) NULL;
CREATE UNIQUE INDEX uk_ai_resume_change_set_source
    ON ai_resume_change_sets(account_id, conversation_id, source_type, source_id);

ALTER TABLE job_match_exports ADD COLUMN request_id VARCHAR(128) NULL;
ALTER TABLE job_match_exports ADD COLUMN request_version INT NULL;
CREATE UNIQUE INDEX uk_job_match_export_request
    ON job_match_exports(account_id, match_id, request_id);

ALTER TABLE job_match_claims ADD COLUMN feedback_request_id VARCHAR(128) NULL;
ALTER TABLE job_match_claims ADD COLUMN version_no INT NOT NULL DEFAULT 0;

