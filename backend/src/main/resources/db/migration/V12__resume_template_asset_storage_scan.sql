ALTER TABLE resume_template_assets ADD COLUMN storage_key VARCHAR(1024) NULL;
ALTER TABLE resume_template_assets ADD COLUMN content_type VARCHAR(128) NULL;
ALTER TABLE resume_template_assets ADD COLUMN size_bytes BIGINT NULL;
ALTER TABLE resume_template_assets ADD COLUMN scan_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SCANNED';
ALTER TABLE resume_template_assets ADD COLUMN scan_report_json LONGTEXT NULL;
ALTER TABLE resume_template_assets ADD COLUMN scanned_at DATETIME(3) NULL;
ALTER TABLE resume_template_assets ADD COLUMN uploaded_by CHAR(36) NULL;

CREATE INDEX idx_resume_template_asset_scan
    ON resume_template_assets(scan_status, status, created_at);
