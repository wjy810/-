-- S0 最小表：账号/会话、任务、导出删除、通知、审计、对象授权与私有文件 Spike。
-- 不为画像、JD、简历、投递、面试预建业务大表。

CREATE TABLE accounts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    password_changed_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_accounts_email UNIQUE (email)
);

CREATE TABLE sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_sessions_token UNIQUE (token_hash)
);
CREATE INDEX idx_sessions_account ON sessions (account_id);

CREATE TABLE password_reset_challenges (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_reset_account ON password_reset_challenges (account_id, created_at);

CREATE TABLE async_tasks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    input_version VARCHAR(64) NOT NULL,
    result_version VARCHAR(64) NULL,
    failure_reason VARCHAR(512) NULL,
    payload_json TEXT NULL,
    attempt_count INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_async_tasks_idem UNIQUE (account_id, task_type, idempotency_key)
);
CREATE INDEX idx_async_tasks_status ON async_tasks (status, updated_at);
CREATE INDEX idx_async_tasks_account ON async_tasks (account_id, created_at);

CREATE TABLE deletion_requests (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id CHAR(36) NULL,
    status VARCHAR(32) NOT NULL,
    impact_summary TEXT NOT NULL,
    legal_exception_note TEXT NOT NULL,
    confirmation_ack TINYINT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_deletion_account ON deletion_requests (account_id, created_at);

CREATE TABLE deletion_receipts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    receipt_status VARCHAR(32) NOT NULL,
    message VARCHAR(512) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_deletion_receipt UNIQUE (request_id, module_code)
);

CREATE TABLE export_requests (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    download_token_hash VARCHAR(64) NULL,
    download_expires_at DATETIME(3) NULL,
    object_key VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_export_task UNIQUE (task_id)
);
CREATE INDEX idx_export_account ON export_requests (account_id, created_at);

CREATE TABLE share_grants (
    id CHAR(36) NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id CHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_share_token UNIQUE (token_hash)
);
CREATE INDEX idx_share_owner ON share_grants (owner_id, created_at);
CREATE INDEX idx_share_resource ON share_grants (resource_type, resource_id);

CREATE TABLE notifications (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    read_at DATETIME(3) NULL,
    CONSTRAINT uk_notification_dedup UNIQUE (account_id, event_id, type)
);
CREATE INDEX idx_notification_account ON notifications (account_id, created_at);

CREATE TABLE audit_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    actor_id CHAR(36) NULL,
    action VARCHAR(64) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id VARCHAR(64) NULL,
    summary VARCHAR(512) NOT NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_audit_actor ON audit_events (actor_id, created_at);
CREATE INDEX idx_audit_object ON audit_events (object_type, object_id);

CREATE TABLE outbox_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) NULL
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, created_at);

CREATE TABLE inbox_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    event_id CHAR(36) NOT NULL,
    consumer_name VARCHAR(64) NOT NULL,
    consumed_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_inbox UNIQUE (event_id, consumer_name)
);

CREATE TABLE idempotency_records (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_idempotency UNIQUE (account_id, idempotency_key)
);

CREATE TABLE private_files (
    id CHAR(36) NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_private_files_owner ON private_files (owner_id, created_at);
