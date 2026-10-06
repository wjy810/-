-- AI career planning recommendations and explicit user-owned target confirmation.

CREATE TABLE career_recommendation_sets (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    profile_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    profile_snapshot_hash CHAR(64) NOT NULL,
    permission_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    task_id CHAR(36) NULL,
    prompt_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NULL,
    response_hash CHAR(64) NULL,
    insufficient_json TEXT NOT NULL,
    confirmation_token_hash CHAR(64) NULL,
    confirmation_token_expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    superseded_at DATETIME(3) NULL
);
CREATE INDEX idx_career_recommendation_set_session ON career_recommendation_sets(session_id, created_at);

CREATE TABLE career_recommendations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    set_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    taxonomy_node_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    recommendation_tier VARCHAR(32) NOT NULL,
    fit_summary VARCHAR(1024) NOT NULL,
    rationale_json TEXT NOT NULL,
    gaps_json TEXT NOT NULL,
    source_refs_json TEXT NOT NULL,
    favorite TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_recommendation_node UNIQUE(set_id, taxonomy_node_id)
);
CREATE INDEX idx_career_recommendation_set ON career_recommendations(set_id, sort_order);

CREATE TABLE career_goals (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    recommendation_id CHAR(36) NOT NULL,
    taxonomy_node_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    confirmed_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_career_goal_account ON career_goals(account_id, status, updated_at);
