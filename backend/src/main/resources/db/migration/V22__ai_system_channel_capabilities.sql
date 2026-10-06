CREATE TABLE ai_channel_capability_test (
    id CHAR(36) NOT NULL PRIMARY KEY,
    channel_id CHAR(36) NOT NULL,
    normalized_base_url VARCHAR(1024) NOT NULL,
    models_json TEXT NOT NULL,
    tested_model VARCHAR(128) NULL,
    authenticated TINYINT(1) NOT NULL,
    model_discovery TINYINT(1) NOT NULL,
    plain_response TINYINT(1) NOT NULL,
    streaming_response TINYINT(1) NOT NULL,
    structured_response TINYINT(1) NOT NULL,
    status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    detail VARCHAR(1024) NOT NULL,
    checked_by CHAR(36) NOT NULL,
    checked_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_ai_channel_capability_latest ON ai_channel_capability_test(channel_id, checked_at);
