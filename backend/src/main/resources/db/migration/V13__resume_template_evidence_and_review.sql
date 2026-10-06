CREATE TABLE resume_template_evidence_artifacts (
    id CHAR(36) PRIMARY KEY,
    evidence_type VARCHAR(32) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    description VARCHAR(512) NOT NULL,
    uploaded_by CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_resume_template_evidence_hash (file_hash)
);

CREATE INDEX idx_resume_template_evidence_type
    ON resume_template_evidence_artifacts(evidence_type, created_at);

ALTER TABLE resume_template_assets ADD COLUMN license_evidence_id CHAR(36) NULL;
ALTER TABLE resume_template_assets ADD COLUMN reviewed_by CHAR(36) NULL;
ALTER TABLE resume_template_assets ADD COLUMN reviewed_at DATETIME(3) NULL;

ALTER TABLE resume_layout_template_versions ADD COLUMN source_asset_id CHAR(36) NULL;
ALTER TABLE resume_layout_template_versions ADD COLUMN independent_design_evidence_id CHAR(36) NULL;

ALTER TABLE resume_template_test_runs ADD COLUMN evidence_artifact_id CHAR(36) NULL;
ALTER TABLE resume_template_test_runs ADD COLUMN template_version_no INT NOT NULL DEFAULT 0;

CREATE INDEX idx_resume_template_version_source_asset
    ON resume_layout_template_versions(source_asset_id);
CREATE INDEX idx_resume_template_test_evidence
    ON resume_template_test_runs(evidence_artifact_id);
