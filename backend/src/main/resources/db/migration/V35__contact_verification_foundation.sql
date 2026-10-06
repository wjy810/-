CREATE TABLE contact_verification_challenges (
    id CHAR(36) NOT NULL PRIMARY KEY,
    channel VARCHAR(16) NOT NULL,
    destination_hash VARCHAR(64) NOT NULL,
    destination_masked VARCHAR(128) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    verification_token_hash VARCHAR(64) NULL,
    expires_at DATETIME(3) NOT NULL,
    verified_at DATETIME(3) NULL,
    consumed_at DATETIME(3) NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_contact_verification_token UNIQUE (verification_token_hash)
);

CREATE INDEX idx_contact_verification_destination
    ON contact_verification_challenges (channel, destination_hash, purpose, created_at);
CREATE INDEX idx_contact_verification_expiry
    ON contact_verification_challenges (expires_at, consumed_at);
