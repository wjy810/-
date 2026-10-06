CREATE TABLE resume_template_test_runs (
    id CHAR(36) PRIMARY KEY,
    template_version_id CHAR(36) NOT NULL,
    gate_code VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    evidence_ref VARCHAR(2048) NOT NULL,
    executed_by CHAR(36) NOT NULL,
    environment_json LONGTEXT NOT NULL,
    report_json LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_resume_template_test_runs_version
    ON resume_template_test_runs(template_version_id, created_at);
