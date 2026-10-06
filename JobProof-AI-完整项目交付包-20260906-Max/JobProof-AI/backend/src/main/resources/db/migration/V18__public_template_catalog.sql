ALTER TABLE resume_template_assets ADD COLUMN original_filename VARCHAR(512) NULL;
ALTER TABLE resume_template_assets ADD COLUMN source_relative_path VARCHAR(1024) NULL;
ALTER TABLE resume_template_assets ADD COLUMN import_batch_id CHAR(36) NULL;

CREATE TABLE resume_template_import_batches (
    id CHAR(36) PRIMARY KEY,
    source_code VARCHAR(64) NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    source_uri VARCHAR(1024) NOT NULL,
    license_evidence_id CHAR(36) NOT NULL,
    distribution_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL,
    processed_count INT NOT NULL,
    ready_count INT NOT NULL,
    duplicate_count INT NOT NULL,
    rejected_count INT NOT NULL,
    quarantined_count INT NOT NULL,
    unsupported_count INT NOT NULL,
    error_message VARCHAR(1024) NULL,
    requested_by CHAR(36) NOT NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version_no INT NOT NULL,
    UNIQUE KEY uk_resume_template_import_source (source_code)
);

CREATE TABLE resume_template_import_items (
    id CHAR(36) PRIMARY KEY,
    batch_id CHAR(36) NOT NULL,
    source_relative_path VARCHAR(1024) NOT NULL,
    source_path_hash CHAR(64) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL,
    file_hash VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    asset_id CHAR(36) NULL,
    duplicate_asset_id CHAR(36) NULL,
    detail_code VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_resume_template_import_item (batch_id, source_path_hash)
);
CREATE INDEX idx_resume_template_import_item_status
    ON resume_template_import_items(batch_id, status, updated_at);

CREATE TABLE resume_template_catalog_entries (
    id CHAR(36) PRIMARY KEY,
    entry_type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    summary VARCHAR(1024) NULL,
    capability VARCHAR(32) NOT NULL,
    asset_kind VARCHAR(32) NOT NULL,
    language_code VARCHAR(16) NOT NULL,
    page_count VARCHAR(16) NULL,
    photo_policy VARCHAR(16) NOT NULL,
    thumbnail_uri VARCHAR(1024) NULL,
    source_name VARCHAR(255) NOT NULL,
    source_uri VARCHAR(1024) NOT NULL,
    attribution VARCHAR(512) NOT NULL,
    search_text LONGTEXT NOT NULL,
    publication_status VARCHAR(32) NOT NULL,
    download_count BIGINT NOT NULL,
    published_at DATETIME(3) NULL,
    retired_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version_no INT NOT NULL,
    UNIQUE KEY uk_resume_template_catalog_reference (entry_type, reference_id)
);
CREATE INDEX idx_resume_template_catalog_public
    ON resume_template_catalog_entries(publication_status, asset_kind, capability, published_at);

CREATE TABLE resume_template_catalog_facets (
    id CHAR(36) PRIMARY KEY,
    catalog_entry_id CHAR(36) NOT NULL,
    facet_type VARCHAR(32) NOT NULL,
    facet_code VARCHAR(64) NOT NULL,
    facet_label VARCHAR(128) NOT NULL,
    UNIQUE KEY uk_resume_template_catalog_facet (catalog_entry_id, facet_type, facet_code)
);
CREATE INDEX idx_resume_template_catalog_facet_lookup
    ON resume_template_catalog_facets(facet_type, facet_code, catalog_entry_id);
