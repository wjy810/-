ALTER TABLE ai_resume_change_sets
    ADD COLUMN base_content_hash CHAR(64) NULL;

ALTER TABLE ai_resume_change_sets
    ADD COLUMN schema_version VARCHAR(32) NOT NULL DEFAULT 'ai-resume-change-set-v1';

ALTER TABLE ai_resume_change_sets
    ADD COLUMN quality_policy_version VARCHAR(32) NOT NULL DEFAULT 'resume-writing-v1';

ALTER TABLE ai_resume_change_sets
    ADD COLUMN summary_text VARCHAR(1024) NULL;

CREATE TABLE ai_resume_change_items (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    change_set_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    module_code VARCHAR(32) NOT NULL,
    target_path VARCHAR(512) NOT NULL,
    operation_code VARCHAR(32) NOT NULL,
    before_value_json TEXT NOT NULL,
    proposed_value_json TEXT NOT NULL,
    corrected_value_json TEXT NULL,
    reason_text VARCHAR(2048) NOT NULL,
    source_facts_json TEXT NOT NULL,
    fact_status VARCHAR(32) NOT NULL,
    quality_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    applied_revision_id CHAR(36) NULL,
    decided_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_resume_change_item_sequence UNIQUE (change_set_id, sequence_no)
);

CREATE INDEX idx_ai_resume_change_item_set
    ON ai_resume_change_items(change_set_id, status, sequence_no);
CREATE INDEX idx_ai_resume_change_item_account
    ON ai_resume_change_items(account_id, created_at);

-- The retired candidate UI must not leave an otherwise editable resume locked in a legacy review state.
UPDATE resume_masters SET status='DRAFT' WHERE status='PENDING_CONFIRMATION';
