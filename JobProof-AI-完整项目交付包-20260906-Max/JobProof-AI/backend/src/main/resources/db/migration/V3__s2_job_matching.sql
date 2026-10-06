-- S2 最小表：JD 原文快照、岗位、岗位版本、预置规则、匹配报告。
-- 解析/匹配任务复用 S0 async_tasks。不建收藏、风险、简历、投递表。

CREATE TABLE matching_rule_snapshots (
    id CHAR(36) NOT NULL PRIMARY KEY,
    version_code VARCHAR(64) NOT NULL,
    published TINYINT NOT NULL,
    weights_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_matching_rule_version UNIQUE (version_code)
);

INSERT INTO matching_rule_snapshots (id, version_code, published, weights_json, created_at)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'P0A-RULE-1',
    1,
    '{"hardGate":30,"skillEvidence":35,"experience":25,"constraints":10}',
    TIMESTAMP '2026-08-18 00:00:00'
);

CREATE TABLE jd_snapshots (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    normalized_hash CHAR(64) NOT NULL,
    original_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_jd_account_hash UNIQUE (account_id, normalized_hash)
);
CREATE INDEX idx_jd_snapshots_account ON jd_snapshots (account_id, created_at);

CREATE TABLE jobs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    current_snapshot_id CHAR(36) NOT NULL,
    company_name VARCHAR(255) NULL,
    title VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_jobs_account ON jobs (account_id, updated_at);
CREATE INDEX idx_jobs_account_company_title ON jobs (account_id, company_name, title);

CREATE TABLE job_versions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    job_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    snapshot_id CHAR(36) NOT NULL,
    parse_task_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(255) NULL,
    company_name VARCHAR(255) NULL,
    duties TEXT NULL,
    hard_skills_json TEXT NULL,
    general_skills_json TEXT NULL,
    plus_skills_json TEXT NULL,
    experience_requirement VARCHAR(512) NULL,
    experience_hard TINYINT NOT NULL,
    education_requirement VARCHAR(255) NULL,
    location VARCHAR(255) NULL,
    work_mode VARCHAR(64) NULL,
    mixed_jobs TINYINT NOT NULL,
    possible_duplicate_job_id CHAR(36) NULL,
    candidate_json TEXT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL
);
CREATE INDEX idx_job_versions_job ON job_versions (job_id, created_at);
CREATE INDEX idx_job_versions_account ON job_versions (account_id, created_at);
CREATE INDEX idx_job_versions_snapshot ON job_versions (snapshot_id);

CREATE TABLE match_reports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    job_version_id CHAR(36) NOT NULL,
    match_task_id CHAR(36) NOT NULL,
    rule_snapshot_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    level VARCHAR(32) NOT NULL,
    total_score INT NULL,
    hard_gate_score INT NULL,
    skill_score INT NULL,
    experience_score INT NULL,
    constraint_score INT NULL,
    weights_json TEXT NOT NULL,
    hard_gap_count INT NOT NULL,
    bonus_applied INT NOT NULL,
    confidence VARCHAR(32) NOT NULL,
    explanation_json TEXT NOT NULL,
    profile_snapshot_version INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_match_reports_job ON match_reports (job_id, created_at);
CREATE INDEX idx_match_reports_account_status ON match_reports (account_id, status, created_at);
CREATE INDEX idx_match_reports_version ON match_reports (job_version_id, status);
