-- S1 最小表：画像正式事实 / 候选、证据及其文件引用、冻结引用闸。
-- 不建 JD、简历主档、投递表。S3 冻结时往 evidence_references 挂真实引用。

CREATE TABLE profile_headers (
    account_id CHAR(36) NOT NULL PRIMARY KEY,
    snapshot_version INT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE profile_facts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    value_json TEXT NOT NULL,
    source VARCHAR(32) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_profile_facts_account_key UNIQUE (account_id, field_key)
);
CREATE INDEX idx_profile_facts_account ON profile_facts (account_id);

CREATE TABLE profile_candidates (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    proposed_value_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    correction_value_json TEXT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL
);
CREATE INDEX idx_profile_candidates_account ON profile_candidates (account_id, created_at);

CREATE TABLE evidences (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NULL,
    core_outcome TEXT NULL,
    url VARCHAR(1024) NULL,
    file_id CHAR(36) NULL,
    sourced_metric TINYINT NOT NULL,
    note VARCHAR(1024) NULL,
    strength VARCHAR(32) NOT NULL,
    pending_supplement TINYINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_evidences_account ON evidences (account_id, updated_at);
CREATE INDEX idx_evidences_account_status ON evidences (account_id, status, updated_at);

CREATE TABLE evidence_references (
    id CHAR(36) NOT NULL PRIMARY KEY,
    evidence_id CHAR(36) NOT NULL,
    resume_version_id CHAR(36) NOT NULL,
    active TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_evidence_ref UNIQUE (evidence_id, resume_version_id)
);
CREATE INDEX idx_evidence_ref_active ON evidence_references (evidence_id, active);
