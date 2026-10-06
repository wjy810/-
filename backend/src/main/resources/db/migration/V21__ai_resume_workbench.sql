ALTER TABLE resume_masters ADD COLUMN content_schema_version VARCHAR(32) NOT NULL DEFAULT 'resume-content-v3';
ALTER TABLE resume_masters ADD COLUMN content_json TEXT NULL;

CREATE TABLE resume_branches (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    parent_branch_id CHAR(36) NULL,
    branch_type VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    language_code VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    job_version_id CHAR(36) NULL,
    current_revision_id CHAR(36) NULL,
    source_revision_id CHAR(36) NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL,
    CONSTRAINT uk_resume_branch_base UNIQUE (master_id, branch_type, job_version_id, language_code)
);
CREATE INDEX idx_resume_branch_master ON resume_branches(master_id, updated_at);

CREATE TABLE resume_revisions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    branch_id CHAR(36) NOT NULL,
    revision_no INT NOT NULL,
    source VARCHAR(32) NOT NULL,
    source_object_id CHAR(36) NULL,
    content_schema_version VARCHAR(32) NOT NULL,
    content_json TEXT NOT NULL,
    layout_instance_id CHAR(36) NULL,
    content_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_resume_revision_no UNIQUE (branch_id, revision_no)
);
CREATE INDEX idx_resume_revision_master ON resume_revisions(master_id, created_at);

CREATE TABLE ai_resume_conversations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    active_branch_id CHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    onboarding_stage VARCHAR(32) NOT NULL,
    identity_type VARCHAR(24) NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    summary_json TEXT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL,
    CONSTRAINT uk_ai_resume_conversation_master UNIQUE (master_id)
);
CREATE INDEX idx_ai_resume_conversation_account ON ai_resume_conversations(account_id, updated_at);

CREATE TABLE ai_resume_messages (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    content_text TEXT NULL,
    client_message_id VARCHAR(128) NULL,
    parent_message_id CHAR(36) NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    current_attempt TINYINT(1) NOT NULL DEFAULT 1,
    error_code VARCHAR(64) NULL,
    model_code VARCHAR(128) NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    prompt_version VARCHAR(64) NULL,
    response_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    CONSTRAINT uk_ai_resume_message_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT uk_ai_resume_message_client UNIQUE (conversation_id, client_message_id)
);
CREATE INDEX idx_ai_resume_message_history ON ai_resume_messages(conversation_id, sequence_no);

CREATE TABLE ai_resume_cards (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    message_id CHAR(36) NULL,
    card_type VARCHAR(40) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    payload_json TEXT NOT NULL,
    validation_json TEXT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_ai_resume_card_conversation ON ai_resume_cards(conversation_id, created_at);

CREATE TABLE ai_resume_change_sets (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    branch_id CHAR(36) NOT NULL,
    message_id CHAR(36) NULL,
    base_revision_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    candidate_ids_json TEXT NOT NULL,
    fact_manifest_json TEXT NOT NULL,
    model_metadata_json TEXT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    applied_revision_id CHAR(36) NULL
);
CREATE INDEX idx_ai_resume_change_conversation ON ai_resume_change_sets(conversation_id, created_at);

CREATE TABLE ai_resume_stream_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_resume_event_sequence UNIQUE (conversation_id, sequence_no)
);
CREATE INDEX idx_ai_resume_event_replay ON ai_resume_stream_events(conversation_id, sequence_no, expires_at);

CREATE TABLE ai_resume_preferences (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    preference_key VARCHAR(64) NOT NULL,
    preference_value VARCHAR(1024) NOT NULL,
    source VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_resume_preference UNIQUE (conversation_id, preference_key)
);

CREATE TABLE ai_user_consents (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    consent_type VARCHAR(32) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    granted_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_user_consent UNIQUE (account_id, consent_type)
);

CREATE TABLE ai_quota_accounts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    period_key CHAR(7) NOT NULL,
    granted_units INT NOT NULL,
    used_units INT NOT NULL DEFAULT 0,
    held_units INT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_quota_period UNIQUE (account_id, period_key)
);

CREATE TABLE ai_quota_reservations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    operation_code VARCHAR(48) NOT NULL,
    reserved_units INT NOT NULL,
    settled_units INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_ai_quota_request UNIQUE (account_id, request_id)
);

CREATE TABLE ai_quota_ledger (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    reservation_id CHAR(36) NULL,
    ledger_type VARCHAR(24) NOT NULL,
    units INT NOT NULL,
    balance_after INT NOT NULL,
    reference_id VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_ai_quota_ledger_account ON ai_quota_ledger(account_id, created_at);

CREATE TABLE job_taxonomy_nodes (
    id CHAR(36) NOT NULL PRIMARY KEY,
    parent_id CHAR(36) NULL,
    node_level VARCHAR(16) NOT NULL,
    code VARCHAR(96) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    catalog_occupation_code VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_job_taxonomy_code UNIQUE (code)
);
CREATE INDEX idx_job_taxonomy_parent ON job_taxonomy_nodes(parent_id, status, sort_order);
CREATE INDEX idx_job_taxonomy_name ON job_taxonomy_nodes(normalized_name, status);

CREATE TABLE job_taxonomy_aliases (
    id CHAR(36) NOT NULL PRIMARY KEY,
    node_id CHAR(36) NOT NULL,
    alias_name VARCHAR(128) NOT NULL,
    normalized_alias VARCHAR(128) NOT NULL,
    language_code VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_job_taxonomy_alias UNIQUE (node_id, normalized_alias)
);
CREATE INDEX idx_job_taxonomy_alias_search ON job_taxonomy_aliases(normalized_alias);

CREATE TABLE ai_mock_interviews (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    branch_id CHAR(36) NOT NULL,
    job_version_id CHAR(36) NULL,
    mode VARCHAR(16) NOT NULL,
    question_limit INT NOT NULL,
    current_question INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    paused_at DATETIME(3) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL
);
CREATE INDEX idx_ai_mock_interview_conversation ON ai_mock_interviews(conversation_id, created_at);

CREATE TABLE ai_mock_interview_turns (
    id CHAR(36) NOT NULL PRIMARY KEY,
    interview_id CHAR(36) NOT NULL,
    turn_no INT NOT NULL,
    question_text TEXT NOT NULL,
    answer_text TEXT NULL,
    feedback_json TEXT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    answered_at DATETIME(3) NULL,
    CONSTRAINT uk_ai_mock_interview_turn UNIQUE (interview_id, turn_no)
);

CREATE TABLE ai_prompt_versions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    prompt_code VARCHAR(64) NOT NULL,
    version_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    prompt_text TEXT NOT NULL,
    schema_json TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    activated_at DATETIME(3) NULL,
    CONSTRAINT uk_ai_prompt_version UNIQUE (prompt_code, version_code)
);

