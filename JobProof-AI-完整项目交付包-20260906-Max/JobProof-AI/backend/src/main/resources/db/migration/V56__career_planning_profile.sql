-- AI career planning: durable sessions, confirmed profile snapshots, evidence grants and interview history.

CREATE TABLE career_planning_sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    phase_code VARCHAR(48) NOT NULL,
    entry_mode VARCHAR(32) NOT NULL,
    ai_consent TINYINT NOT NULL DEFAULT 0,
    current_profile_id CHAR(36) NULL,
    current_recommendation_set_id CHAR(36) NULL,
    current_goal_id CHAR(36) NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_career_planning_session_account ON career_planning_sessions(account_id, status, updated_at);

CREATE TABLE career_planning_profiles (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    entry_mode VARCHAR(32) NOT NULL,
    objective_taxonomy_id CHAR(36) NULL,
    basics_json TEXT NOT NULL,
    preferences_json TEXT NOT NULL,
    constraints_json TEXT NOT NULL,
    snapshot_hash CHAR(64) NULL,
    snapshot_version INT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    CONSTRAINT uk_career_planning_profile_session UNIQUE(session_id)
);
CREATE INDEX idx_career_planning_profile_account ON career_planning_profiles(account_id, updated_at);

CREATE TABLE career_planning_profile_items (
    id CHAR(36) NOT NULL PRIMARY KEY,
    profile_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    section_code VARCHAR(32) NOT NULL,
    claim_type VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    source_refs_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    confirmed TINYINT NOT NULL DEFAULT 0,
    locked TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_career_planning_item_profile ON career_planning_profile_items(profile_id, section_code, sort_order);

CREATE TABLE career_planning_evidence_permissions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    profile_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id CHAR(36) NOT NULL,
    source_version INT NOT NULL,
    scopes_json TEXT NOT NULL,
    snapshot_json TEXT NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    permission_version INT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL
);
CREATE INDEX idx_career_planning_permission_profile ON career_planning_evidence_permissions(profile_id, status, created_at);

CREATE TABLE career_planning_interview_rounds (
    id CHAR(36) NOT NULL PRIMARY KEY,
    profile_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    round_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    questions_json TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    model_code VARCHAR(128) NULL,
    response_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    CONSTRAINT uk_career_planning_interview_round UNIQUE(profile_id, round_no)
);
CREATE INDEX idx_career_planning_interview_profile ON career_planning_interview_rounds(profile_id, round_no);

CREATE TABLE career_planning_messages (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    round_id CHAR(36) NULL,
    account_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    role_code VARCHAR(24) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    body_text TEXT NULL,
    payload_json TEXT NOT NULL,
    response_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_planning_message_sequence UNIQUE(session_id, sequence_no)
);
CREATE INDEX idx_career_planning_message_session ON career_planning_messages(session_id, sequence_no);

CREATE TABLE career_planning_stream_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_planning_event_sequence UNIQUE(session_id, sequence_no)
);
CREATE INDEX idx_career_planning_event_replay ON career_planning_stream_events(session_id, sequence_no, expires_at);
