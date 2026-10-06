ALTER TABLE resume_branches ADD COLUMN review_metadata_json TEXT NULL;

CREATE TABLE ai_resume_generation_attempts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    operation_code VARCHAR(48) NOT NULL,
    task_class VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    cancel_requested TINYINT(1) NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    model_code VARCHAR(128) NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_resume_attempt_request UNIQUE (conversation_id, request_id, operation_code)
);
CREATE INDEX idx_ai_resume_attempt_account ON ai_resume_generation_attempts(account_id, completed_at);
