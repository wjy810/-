ALTER TABLE match_reports ADD COLUMN report_protocol_version VARCHAR(32) NOT NULL DEFAULT 'match-report-v1';
ALTER TABLE match_reports ADD COLUMN resume_master_id CHAR(36) NULL;
ALTER TABLE match_reports ADD COLUMN resume_branch_id CHAR(36) NULL;
ALTER TABLE match_reports ADD COLUMN resume_revision_id CHAR(36) NULL;
ALTER TABLE match_reports ADD COLUMN resume_content_hash CHAR(64) NULL;
ALTER TABLE match_reports ADD COLUMN resume_source_type VARCHAR(32) NULL;
ALTER TABLE match_reports ADD COLUMN resume_analysis_json TEXT NULL;

CREATE INDEX idx_match_reports_resume_revision ON match_reports(resume_revision_id, created_at);

CREATE TABLE resume_import_sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    career_file_id CHAR(36) NULL,
    source_text TEXT NULL,
    source_filename VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    structured_draft_json TEXT NULL,
    source_map_json TEXT NULL,
    confidence_json TEXT NULL,
    parse_task_id CHAR(36) NULL,
    error_code VARCHAR(64) NULL,
    result_master_id CHAR(36) NULL,
    result_branch_id CHAR(36) NULL,
    result_revision_id CHAR(36) NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL
);
CREATE INDEX idx_resume_import_account ON resume_import_sessions(account_id, created_at);
CREATE INDEX idx_resume_import_task ON resume_import_sessions(parse_task_id);

CREATE TABLE match_requirement_items (
    id CHAR(36) NOT NULL PRIMARY KEY,
    report_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    requirement_type VARCHAR(32) NOT NULL,
    requirement_text VARCHAR(2048) NOT NULL,
    resume_refs_json TEXT NOT NULL,
    evidence_refs_json TEXT NOT NULL,
    judgement_status VARCHAR(40) NOT NULL,
    rule_basis VARCHAR(2048) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_match_requirement_sequence UNIQUE(report_id, sequence_no)
);
CREATE INDEX idx_match_requirement_report ON match_requirement_items(report_id, sequence_no);

CREATE TABLE match_ai_advice_runs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    report_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    response_hash CHAR(64) NULL,
    quota_reservation_id CHAR(36) NULL,
    error_code VARCHAR(64) NULL,
    summary_text TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL
);
CREATE INDEX idx_match_advice_run_report ON match_ai_advice_runs(report_id, created_at);

CREATE TABLE match_advice_items (
    id CHAR(36) NOT NULL PRIMARY KEY,
    run_id CHAR(36) NOT NULL,
    report_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    requirement_item_id CHAR(36) NULL,
    sequence_no INT NOT NULL,
    priority_code VARCHAR(8) NOT NULL,
    advice_type VARCHAR(24) NOT NULL,
    target_path VARCHAR(512) NULL,
    before_value_json TEXT NULL,
    proposed_value_json TEXT NULL,
    reason_text VARCHAR(2048) NOT NULL,
    source_refs_json TEXT NOT NULL,
    learning_plan_json TEXT NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    applied_revision_id CHAR(36) NULL,
    decided_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_match_advice_sequence UNIQUE(run_id, sequence_no)
);
CREATE INDEX idx_match_advice_report ON match_advice_items(report_id, status, sequence_no);
