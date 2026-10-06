-- S3 最小表：简历主档、AI 候选、冻结版本、投递与阶段时间线。
-- 不建收藏、漏斗、面试轮次表。证据引用闸复用 S1 evidence_references。

CREATE TABLE resume_masters (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_before_archive VARCHAR(32) NULL,
    source VARCHAR(32) NOT NULL,
    template_code VARCHAR(32) NULL,
    education_json TEXT NULL,
    experience_json TEXT NULL,
    projects_json TEXT NULL,
    skills_json TEXT NULL,
    certificates_json TEXT NULL,
    self_intro TEXT NULL,
    key_outcomes_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_resume_masters_account ON resume_masters (account_id, updated_at);

CREATE TABLE resume_candidates (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    proposed_value_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    correction_value_json TEXT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL
);
CREATE INDEX idx_resume_candidates_master ON resume_candidates (master_id, created_at);
CREATE INDEX idx_resume_candidates_account ON resume_candidates (account_id, created_at);

CREATE TABLE resume_versions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    customize_task_id CHAR(36) NULL,
    job_version_id CHAR(36) NULL,
    snapshot_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    frozen_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_resume_versions_master ON resume_versions (master_id, created_at);
CREATE INDEX idx_resume_versions_account ON resume_versions (account_id, created_at);

CREATE TABLE applications (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    resume_version_id CHAR(36) NOT NULL,
    job_version_id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    channel VARCHAR(128) NULL,
    applied_at DATETIME(3) NULL,
    note VARCHAR(2048) NULL,
    company_alias VARCHAR(255) NULL,
    job_company_snapshot VARCHAR(255) NULL,
    job_title_snapshot VARCHAR(255) NULL,
    archived TINYINT NOT NULL,
    main_stack_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_applications_account ON applications (account_id, updated_at);
CREATE INDEX idx_applications_resume_version ON applications (resume_version_id);
CREATE INDEX idx_applications_job_version ON applications (job_version_id);

CREATE TABLE application_stage_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    application_id CHAR(36) NOT NULL,
    actor_account_id CHAR(36) NOT NULL,
    source VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(1024) NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_application_events_app ON application_stage_events (application_id, created_at);
