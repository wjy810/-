CREATE TABLE changelog_releases (
    id CHAR(36) NOT NULL PRIMARY KEY,
    version_label VARCHAR(32) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    release_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    modules_json TEXT NOT NULL,
    cta_label VARCHAR(64) NULL,
    cta_path VARCHAR(500) NULL,
    show_whats_new BOOLEAN NOT NULL DEFAULT FALSE,
    send_notification BOOLEAN NOT NULL DEFAULT TRUE,
    scheduled_at DATETIME(3) NULL,
    published_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL,
    current_revision INT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_by CHAR(36) NOT NULL,
    updated_by CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_changelog_version UNIQUE (version_label),
    CONSTRAINT uk_changelog_slug UNIQUE (slug)
);
CREATE INDEX idx_changelog_public ON changelog_releases (status, published_at);
CREATE INDEX idx_changelog_schedule ON changelog_releases (status, scheduled_at);

CREATE TABLE changelog_revisions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    release_id CHAR(36) NOT NULL,
    revision_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    release_type VARCHAR(32) NOT NULL,
    modules_json TEXT NOT NULL,
    cta_label VARCHAR(64) NULL,
    cta_path VARCHAR(500) NULL,
    correction_reason VARCHAR(500) NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_changelog_revision UNIQUE (release_id, revision_no)
);

CREATE TABLE changelog_sections (
    id CHAR(36) NOT NULL PRIMARY KEY,
    revision_id CHAR(36) NOT NULL,
    section_type VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body_text TEXT NOT NULL,
    items_json TEXT NOT NULL,
    sort_order INT NOT NULL
);
CREATE INDEX idx_changelog_sections_revision ON changelog_sections (revision_id, sort_order);

CREATE TABLE changelog_user_receipts (
    release_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    first_seen_at DATETIME(3) NULL,
    read_at DATETIME(3) NULL,
    acknowledged_at DATETIME(3) NULL,
    remind_after DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (release_id, account_id)
);
CREATE INDEX idx_changelog_receipt_account ON changelog_user_receipts (account_id, updated_at);

CREATE TABLE changelog_distribution_jobs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    release_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    cursor_account_id CHAR(36) NULL,
    delivered_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    CONSTRAINT uk_changelog_distribution UNIQUE (release_id)
);
CREATE INDEX idx_changelog_distribution_status ON changelog_distribution_jobs (status, updated_at);

CREATE TABLE changelog_assets (
    id CHAR(36) NOT NULL PRIMARY KEY,
    release_id CHAR(36) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_changelog_assets_release ON changelog_assets (release_id, status);

ALTER TABLE notifications ADD COLUMN action_path VARCHAR(500) NULL;
