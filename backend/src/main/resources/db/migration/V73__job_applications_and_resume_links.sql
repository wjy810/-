-- 投递追踪与简历查看回执 (docs/phase2/01-投递追踪与查看回执.md)
-- One row per company the user applied to (or wants to), a timeline of what happened, and per-company
-- resume links whose openings are recorded without keeping the viewer's IP address.

CREATE TABLE job_applications (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    company VARCHAR(120) NOT NULL,
    position_title VARCHAR(120) NOT NULL,
    location VARCHAR(80) NULL,
    channel VARCHAR(24) NOT NULL,
    job_url VARCHAR(500) NULL,
    stage VARCHAR(24) NOT NULL,
    furthest_stage VARCHAR(24) NOT NULL,
    outcome VARCHAR(24) NULL,
    salary_text VARCHAR(60) NULL,
    resume_version_id CHAR(36) NULL,
    job_match_id CHAR(36) NULL,
    applied_on DATE NULL,
    next_step VARCHAR(200) NULL,
    next_step_at DATETIME(3) NULL,
    notes TEXT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_job_applications_account ON job_applications(account_id, updated_at);

CREATE TABLE job_application_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    application_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_stage VARCHAR(24) NULL,
    to_stage VARCHAR(24) NULL,
    detail VARCHAR(300) NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_job_application_events_app ON job_application_events(application_id, created_at);

CREATE TABLE resume_share_links (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    token VARCHAR(32) NOT NULL,
    resume_version_id CHAR(36) NOT NULL,
    application_id CHAR(36) NULL,
    label VARCHAR(120) NOT NULL,
    hide_contact BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    view_count INT NOT NULL DEFAULT 0,
    visitor_count INT NOT NULL DEFAULT 0,
    read_seconds INT NOT NULL DEFAULT 0,
    first_viewed_at DATETIME(3) NULL,
    last_viewed_at DATETIME(3) NULL,
    notified_visitors INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_resume_share_token UNIQUE (token)
);
CREATE INDEX idx_resume_share_links_account ON resume_share_links(account_id, created_at);
CREATE INDEX idx_resume_share_links_application ON resume_share_links(application_id);

-- visitor_hash = sha256(link, address, user agent): tells repeat visits from new visitors, keeps no address.
CREATE TABLE resume_share_views (
    id CHAR(36) NOT NULL PRIMARY KEY,
    link_id CHAR(36) NOT NULL,
    view_key CHAR(64) NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    device VARCHAR(16) NOT NULL,
    client VARCHAR(16) NOT NULL,
    referrer_host VARCHAR(120) NULL,
    started_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    read_seconds INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_resume_share_views_link ON resume_share_views(link_id, started_at);
CREATE INDEX idx_resume_share_views_visitor ON resume_share_views(link_id, visitor_hash, last_seen_at);
