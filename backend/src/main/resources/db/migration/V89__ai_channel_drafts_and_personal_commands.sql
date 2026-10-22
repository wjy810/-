ALTER TABLE ai_channel ADD COLUMN routing_weight INT NOT NULL DEFAULT 100;
ALTER TABLE ai_channel ADD COLUMN config_version INT NOT NULL DEFAULT 0;
ALTER TABLE ai_channel ADD COLUMN applied_config_version INT NOT NULL DEFAULT 0;
ALTER TABLE ai_channel ADD COLUMN pending_base_url VARCHAR(1024) NULL;
ALTER TABLE ai_channel ADD COLUMN pending_protocol VARCHAR(32) NULL;
ALTER TABLE ai_channel ADD COLUMN pending_api_key_ciphertext TEXT NULL;
ALTER TABLE ai_channel ADD COLUMN pending_api_key_masked VARCHAR(32) NULL;
ALTER TABLE ai_channel ADD COLUMN pending_model VARCHAR(128) NULL;
ALTER TABLE ai_channel ADD COLUMN last_capability_id CHAR(36) NULL;
ALTER TABLE ai_channel_capability_test ADD COLUMN config_version INT NOT NULL DEFAULT -1;
CREATE TABLE ai_channel_commands (
    account_id CHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_id CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id, operation, idempotency_key)
);
