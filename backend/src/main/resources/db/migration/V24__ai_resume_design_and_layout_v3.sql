ALTER TABLE resume_layout_instances ADD COLUMN branch_id CHAR(36) NULL;
ALTER TABLE resume_layout_instances ADD COLUMN design_schema_version VARCHAR(32) NULL;
ALTER TABLE resume_layout_instances ADD COLUMN design_json LONGTEXT NULL;

CREATE INDEX idx_resume_layout_instances_branch
    ON resume_layout_instances(account_id, branch_id, created_at);

CREATE TABLE resume_layout_preferences (
    id CHAR(36) PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    branch_id CHAR(36) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    variant_code VARCHAR(64) NOT NULL,
    design_schema_version VARCHAR(32) NOT NULL,
    settings_json LONGTEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_resume_layout_preference (account_id, branch_id, template_id)
);

CREATE INDEX idx_resume_layout_preference_master
    ON resume_layout_preferences(account_id, master_id, updated_at);

-- The existing twelve definitions are development drafts. V3 normalizes their
-- full slot set and design capabilities in the server without mutating snapshots.
UPDATE resume_layout_template_versions
SET renderer_protocol='resume-layout-v3', version_no=version_no+1, updated_at=CURRENT_TIMESTAMP(3)
WHERE status='DRAFT' AND template_id IN (
    'rlt-b-ats-minimal-v1',
    'rlt-b-tech-single-v1',
    'rlt-b-tech-double-v1',
    'rlt-b-campus-v1',
    'rlt-b-career-pro-v1',
    'rlt-b-consulting-v1',
    'rlt-b-finance-v1',
    'rlt-b-product-ops-v1',
    'rlt-b-education-research-v1',
    'rlt-b-english-single-v1',
    'rlt-b-cn-table-v1',
    'rlt-b-qa-data-v1'
);
