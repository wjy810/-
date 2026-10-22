CREATE TABLE changelog_publication_snapshots (
    release_id VARCHAR(36) PRIMARY KEY,
    revision_no INT NOT NULL,
    send_notification BOOLEAN NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    slug VARCHAR(200) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    published_at TIMESTAMP(3) NOT NULL
);
-- Existing publications lack a verifiable immutable confirmation. Do not start
-- new campaigns for their queued outbox events after upgrading.
UPDATE changelog_distribution_jobs SET status='CANCELLED' WHERE status IN ('PENDING','RUNNING');

CREATE TABLE ai_suggestion_results (
    account_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(240) NOT NULL,
    operation_code VARCHAR(80) NOT NULL,
    request_path VARCHAR(512) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    owner_token VARCHAR(36),
    lease_until TIMESTAMP(3),
    reservation_id VARCHAR(36),
    result_json LONGTEXT,
    created_at TIMESTAMP(3) NOT NULL,
    completed_at TIMESTAMP(3),
    PRIMARY KEY(account_id,request_key)
);
