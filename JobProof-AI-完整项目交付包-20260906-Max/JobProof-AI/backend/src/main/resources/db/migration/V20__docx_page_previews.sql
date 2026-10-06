ALTER TABLE resume_template_catalog_entries ADD COLUMN preview_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';
ALTER TABLE resume_template_catalog_entries ADD COLUMN preview_page_count INT NOT NULL DEFAULT 0;
ALTER TABLE resume_template_catalog_entries ADD COLUMN preview_error VARCHAR(1024) NULL;
ALTER TABLE resume_template_catalog_entries ADD COLUMN preview_renderer_version VARCHAR(128) NULL;
ALTER TABLE resume_template_catalog_entries ADD COLUMN preview_updated_at DATETIME(3) NULL;

UPDATE resume_template_catalog_entries
SET preview_status = 'PENDING',
    preview_page_count = 0,
    preview_error = NULL,
    preview_renderer_version = NULL,
    preview_updated_at = updated_at,
    thumbnail_uri = NULL
WHERE entry_type = 'DOCX_ASSET';

CREATE INDEX idx_resume_template_catalog_preview
    ON resume_template_catalog_entries(entry_type, preview_status, preview_updated_at);
