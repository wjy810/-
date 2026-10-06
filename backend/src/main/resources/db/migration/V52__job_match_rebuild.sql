-- JobProof AI job-match aggregate, evidence snapshots, resumable analysis and exports.
-- Existing V3/V11/V34/V36 rows remain readable; this migration only adds the v2 workflow.

CREATE TABLE job_match_tasks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    job_version_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    resume_master_id CHAR(36) NULL,
    resume_branch_id CHAR(36) NULL,
    resume_revision_id CHAR(36) NULL,
    resume_import_id CHAR(36) NULL,
    authorization_id CHAR(36) NULL,
    current_report_id CHAR(36) NULL,
    analysis_task_id CHAR(36) NULL,
    analysis_request_id VARCHAR(128) NULL,
    quota_reservation_id CHAR(36) NULL,
    evidence_mode VARCHAR(24) NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    checkpoint_code VARCHAR(48) NULL,
    output_options_json TEXT NULL,
    error_code VARCHAR(64) NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL,
    CONSTRAINT uk_job_match_analysis_request UNIQUE (account_id, analysis_request_id)
);
CREATE INDEX idx_job_match_tasks_account ON job_match_tasks(account_id, updated_at);
CREATE INDEX idx_job_match_tasks_status ON job_match_tasks(status, updated_at);

CREATE TABLE job_requirements (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    category VARCHAR(32) NOT NULL,
    requirement_text VARCHAR(2048) NOT NULL,
    priority_code VARCHAR(24) NOT NULL,
    hard_gate TINYINT NOT NULL,
    source_locator VARCHAR(512) NOT NULL,
    source_quote VARCHAR(2048) NOT NULL,
    confidence INT NOT NULL,
    user_corrected TINYINT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_job_requirement_sequence UNIQUE(match_id, sequence_no)
);
CREATE INDEX idx_job_requirements_match ON job_requirements(match_id, sequence_no);

CREATE TABLE job_match_authorizations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    scope_code VARCHAR(32) NOT NULL,
    included_sources_json TEXT NOT NULL,
    excluded_fields_json TEXT NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    remember_preference TINYINT NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL
);
CREATE INDEX idx_job_match_authorization ON job_match_authorizations(match_id, status, created_at);

CREATE TABLE job_match_evidence_items (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    authorization_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    excerpt TEXT NULL,
    locator VARCHAR(512) NULL,
    strength VARCHAR(24) NOT NULL,
    relevance INT NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_job_match_evidence_source UNIQUE(match_id, authorization_id, source_type, source_id)
);
CREATE INDEX idx_job_match_evidence_match ON job_match_evidence_items(match_id, relevance);

CREATE TABLE job_match_claims (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    report_id CHAR(36) NOT NULL,
    requirement_id CHAR(36) NOT NULL,
    conclusion_type VARCHAR(40) NOT NULL,
    score INT NULL,
    confidence INT NOT NULL,
    evidence_ids_json TEXT NOT NULL,
    reasoning_summary VARCHAR(2048) NOT NULL,
    feedback_status VARCHAR(24) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_job_match_claims_report ON job_match_claims(report_id, requirement_id);

CREATE TABLE job_match_clarifications (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    requirement_id CHAR(36) NULL,
    sequence_no INT NOT NULL,
    question_text VARCHAR(2048) NOT NULL,
    options_json TEXT NOT NULL,
    evidence_context_json TEXT NOT NULL,
    answer_code VARCHAR(64) NULL,
    answer_note VARCHAR(1024) NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    answered_at DATETIME(3) NULL,
    CONSTRAINT uk_job_match_clarification_sequence UNIQUE(match_id, sequence_no)
);
CREATE INDEX idx_job_match_clarifications ON job_match_clarifications(match_id, status, sequence_no);

CREATE TABLE job_match_report_versions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    report_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    report_schema_version VARCHAR(32) NOT NULL,
    report_json TEXT NOT NULL,
    input_hash CHAR(64) NOT NULL,
    model_code VARCHAR(128) NULL,
    prompt_version VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_job_match_report_version UNIQUE(match_id, version_no)
);
CREATE INDEX idx_job_match_report_versions ON job_match_report_versions(match_id, created_at);

CREATE TABLE job_match_improvement_tasks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    report_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    gap_code VARCHAR(64) NOT NULL,
    phase_code VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    task_text VARCHAR(2048) NOT NULL,
    expected_output VARCHAR(1024) NOT NULL,
    acceptance_criteria VARCHAR(1024) NOT NULL,
    estimated_hours INT NOT NULL,
    priority_code VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_job_match_improvements ON job_match_improvement_tasks(match_id, phase_code, status);

CREATE TABLE job_match_stream_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_job_match_event_sequence UNIQUE(match_id, sequence_no)
);
CREATE INDEX idx_job_match_event_replay ON job_match_stream_events(match_id, sequence_no, expires_at);

CREATE TABLE job_match_exports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    match_id CHAR(36) NOT NULL,
    report_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    task_id CHAR(36) NULL,
    format_code VARCHAR(16) NOT NULL,
    sections_json TEXT NOT NULL,
    redacted TINYINT NOT NULL,
    language_code VARCHAR(16) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    private_file_id CHAR(36) NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(64) NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL
);
CREATE INDEX idx_job_match_exports ON job_match_exports(account_id, match_id, created_at);

ALTER TABLE match_reports ADD COLUMN match_id CHAR(36) NULL;
ALTER TABLE match_reports ADD COLUMN report_version_id CHAR(36) NULL;
ALTER TABLE match_reports ADD COLUMN recommendation_code VARCHAR(32) NULL;
ALTER TABLE match_reports ADD COLUMN dimension_scores_json TEXT NULL;

CREATE INDEX idx_match_reports_match ON match_reports(match_id, created_at);

