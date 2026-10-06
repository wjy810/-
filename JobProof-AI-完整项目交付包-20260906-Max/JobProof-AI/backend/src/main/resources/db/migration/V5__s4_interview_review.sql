-- S4 最小表：面试轮次、复盘报告与建议。
-- 不建录音、转写、准备包、模拟面试、工作台卡片。轮次/复盘不得回写投递主状态。

CREATE TABLE interview_rounds (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    custom_name VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    status_before_cancel VARCHAR(32) NULL,
    scheduled_at DATETIME(3) NULL,
    timezone VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    note VARCHAR(2048) NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_interview_rounds_account ON interview_rounds (account_id, updated_at);
CREATE INDEX idx_interview_rounds_app ON interview_rounds (application_id, created_at);

CREATE TABLE review_reports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    interview_round_id CHAR(36) NULL,
    status VARCHAR(32) NOT NULL,
    input_source VARCHAR(32) NOT NULL,
    input_text TEXT NOT NULL,
    resume_version_id CHAR(36) NOT NULL,
    job_version_id CHAR(36) NOT NULL,
    analyze_task_id CHAR(36) NULL,
    failure_reason VARCHAR(1024) NULL,
    questions_json TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_review_reports_account ON review_reports (account_id, updated_at);
CREATE INDEX idx_review_reports_app ON review_reports (application_id, created_at);
CREATE INDEX idx_review_reports_task ON review_reports (analyze_task_id);

CREATE TABLE review_suggestions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    report_id CHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    text VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL
);
CREATE INDEX idx_review_suggestions_report ON review_suggestions (report_id, ordinal);
CREATE INDEX idx_review_suggestions_account ON review_suggestions (account_id, created_at);
