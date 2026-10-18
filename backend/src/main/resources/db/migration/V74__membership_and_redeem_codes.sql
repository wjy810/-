-- 会员与兑换码 (docs/phase2/02-会员与兑换码.md)
-- A plan per account with an expiry, its history, and redeem codes issued in batches (for schools,
-- training providers and promotions). Codes are stored only as hashes; the plain codes are shown once.

CREATE TABLE account_memberships (
    account_id CHAR(36) NOT NULL PRIMARY KEY,
    plan VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NULL,
    source VARCHAR(32) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE membership_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    plan VARCHAR(16) NOT NULL,
    days INT NOT NULL,
    batch_id CHAR(36) NULL,
    expires_at DATETIME(3) NULL,
    detail VARCHAR(200) NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_membership_events_account ON membership_events(account_id, created_at);

CREATE TABLE redeem_code_batches (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    channel VARCHAR(120) NULL,
    plan VARCHAR(16) NOT NULL,
    duration_days INT NOT NULL,
    quantity INT NOT NULL,
    redeemed_count INT NOT NULL DEFAULT 0,
    redeem_before DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_redeem_code_batches_created ON redeem_code_batches(created_at);

CREATE TABLE redeem_codes (
    id CHAR(36) NOT NULL PRIMARY KEY,
    batch_id CHAR(36) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    code_hint VARCHAR(8) NOT NULL,
    redeemed_by CHAR(36) NULL,
    redeemed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_redeem_code_hash UNIQUE (code_hash)
);
CREATE INDEX idx_redeem_codes_batch ON redeem_codes(batch_id);

-- Failed attempts per account, so codes cannot be guessed by trying many.
CREATE TABLE redeem_attempts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    succeeded BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_redeem_attempts_account ON redeem_attempts(account_id, created_at);
