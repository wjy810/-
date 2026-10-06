-- AI channel pool, model pricing, wallet, subscription, usage, billing, health and audit.
CREATE TABLE ai_channel (id CHAR(36) NOT NULL PRIMARY KEY, scope VARCHAR(16) NOT NULL, owner_account_id CHAR(36) NULL, name VARCHAR(128) NOT NULL, provider_code VARCHAR(64) NOT NULL, base_url VARCHAR(1024) NOT NULL, protocol VARCHAR(32) NOT NULL, api_key_ciphertext TEXT NOT NULL, api_key_masked VARCHAR(32) NOT NULL, channel_rate DECIMAL(19,8) NOT NULL DEFAULT 1.00000000, status VARCHAR(32) NOT NULL, version_no INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, archived_at DATETIME(3) NULL, CONSTRAINT uk_ai_channel_owner_name UNIQUE (owner_account_id, name));
CREATE INDEX idx_ai_channel_route ON ai_channel (scope, status, provider_code);

CREATE TABLE ai_model (id CHAR(36) NOT NULL PRIMARY KEY, model_code VARCHAR(128) NOT NULL, display_name VARCHAR(128) NOT NULL, provider_code VARCHAR(64) NOT NULL, model_type VARCHAR(32) NOT NULL, billing_mode VARCHAR(16) NOT NULL, unit VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, version_no INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, CONSTRAINT uk_ai_model_code UNIQUE (model_code));

CREATE TABLE ai_channel_model (id CHAR(36) NOT NULL PRIMARY KEY, channel_id CHAR(36) NOT NULL, model_id CHAR(36) NOT NULL, provider_model_code VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, hidden TINYINT(1) NOT NULL DEFAULT 0, priority INT NOT NULL DEFAULT 100, version_no INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, CONSTRAINT uk_ai_channel_model UNIQUE (channel_id, model_id));
CREATE INDEX idx_ai_channel_model_route ON ai_channel_model (model_id, status, hidden, priority);

CREATE TABLE ai_model_pricing (id CHAR(36) NOT NULL PRIMARY KEY, model_id CHAR(36) NOT NULL, fixed_price DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, model_rate DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, cache_rate DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, completion_rate DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, currency CHAR(3) NOT NULL DEFAULT 'USD', status VARCHAR(32) NOT NULL, effective_from DATETIME(3) NOT NULL, effective_to DATETIME(3) NULL, version_no INT NOT NULL DEFAULT 0, created_by CHAR(36) NULL, created_at DATETIME(3) NOT NULL);
CREATE INDEX idx_ai_pricing_effective ON ai_model_pricing (model_id, status, effective_from, effective_to);

CREATE TABLE wallet_account (id CHAR(36) NOT NULL PRIMARY KEY, account_id CHAR(36) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'USD', available_balance DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, held_balance DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, version_no INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, CONSTRAINT uk_wallet_account UNIQUE (account_id, currency));

CREATE TABLE wallet_ledger (id CHAR(36) NOT NULL PRIMARY KEY, wallet_account_id CHAR(36) NOT NULL, account_id CHAR(36) NOT NULL, ledger_type VARCHAR(32) NOT NULL, amount DECIMAL(19,8) NOT NULL, balance_after DECIMAL(19,8) NOT NULL, reference_type VARCHAR(32) NOT NULL, reference_id CHAR(36) NULL, idempotency_key VARCHAR(128) NOT NULL, description VARCHAR(512) NULL, created_at DATETIME(3) NOT NULL, CONSTRAINT uk_wallet_ledger_idem UNIQUE (account_id, ledger_type, idempotency_key));
CREATE INDEX idx_wallet_ledger_account ON wallet_ledger (account_id, created_at);

CREATE TABLE wallet_hold (id CHAR(36) NOT NULL PRIMARY KEY, wallet_account_id CHAR(36) NOT NULL, account_id CHAR(36) NOT NULL, request_id VARCHAR(128) NOT NULL, amount DECIMAL(19,8) NOT NULL, settled_amount DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, status VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, expires_at DATETIME(3) NOT NULL, version_no INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, CONSTRAINT uk_wallet_hold_request UNIQUE (account_id, request_id), CONSTRAINT uk_wallet_hold_idem UNIQUE (account_id, idempotency_key));

CREATE TABLE personal_channel_subscription (id CHAR(36) NOT NULL PRIMARY KEY, account_id CHAR(36) NOT NULL, status VARCHAR(32) NOT NULL, price DECIMAL(19,8) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'USD', current_period_start DATETIME(3) NOT NULL, current_period_end DATETIME(3) NOT NULL, auto_renew TINYINT(1) NOT NULL DEFAULT 1, idempotency_key VARCHAR(128) NOT NULL, version_no INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, CONSTRAINT uk_personal_subscription_account UNIQUE (account_id), CONSTRAINT uk_personal_subscription_idem UNIQUE (account_id, idempotency_key));

CREATE TABLE ai_usage_record (id CHAR(36) NOT NULL PRIMARY KEY, request_id VARCHAR(128) NOT NULL, account_id CHAR(36) NOT NULL, channel_id CHAR(36) NOT NULL, model_id CHAR(36) NOT NULL, status VARCHAR(32) NOT NULL, input_tokens BIGINT NOT NULL DEFAULT 0, cached_input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0, requested_units INT NOT NULL DEFAULT 0, successful_units INT NOT NULL DEFAULT 0, provider_usage_json TEXT NULL, failure_code VARCHAR(64) NULL, started_at DATETIME(3) NOT NULL, completed_at DATETIME(3) NULL, CONSTRAINT uk_ai_usage_request UNIQUE (request_id));
CREATE INDEX idx_ai_usage_account ON ai_usage_record (account_id, started_at);

CREATE TABLE ai_billing_record (id CHAR(36) NOT NULL PRIMARY KEY, usage_id CHAR(36) NOT NULL, account_id CHAR(36) NOT NULL, wallet_hold_id CHAR(36) NULL, status VARCHAR(32) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'USD', estimated_amount DECIMAL(19,8) NOT NULL, actual_amount DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, refunded_amount DECIMAL(19,8) NOT NULL DEFAULT 0.00000000, pricing_id CHAR(36) NULL, pricing_snapshot TEXT NOT NULL, channel_rate_snapshot DECIMAL(19,8) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, version_no INT NOT NULL DEFAULT 0, settled_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, CONSTRAINT uk_ai_billing_usage UNIQUE (usage_id), CONSTRAINT uk_ai_billing_idem UNIQUE (account_id, idempotency_key));
CREATE INDEX idx_ai_billing_account ON ai_billing_record (account_id, created_at);

CREATE TABLE ai_channel_health (id CHAR(36) NOT NULL PRIMARY KEY, channel_id CHAR(36) NOT NULL, status VARCHAR(32) NOT NULL, latency_ms BIGINT NULL, consecutive_failures INT NOT NULL DEFAULT 0, failure_code VARCHAR(64) NULL, checked_at DATETIME(3) NOT NULL, version_no INT NOT NULL DEFAULT 0, CONSTRAINT uk_ai_channel_health UNIQUE (channel_id));

CREATE TABLE ai_audit_log (id CHAR(36) NOT NULL PRIMARY KEY, actor_account_id CHAR(36) NULL, action VARCHAR(64) NOT NULL, object_type VARCHAR(64) NOT NULL, object_id VARCHAR(64) NULL, result VARCHAR(32) NOT NULL, trace_id VARCHAR(128) NULL, summary VARCHAR(1024) NOT NULL, created_at DATETIME(3) NOT NULL);
CREATE INDEX idx_ai_audit_actor ON ai_audit_log (actor_account_id, created_at);
CREATE INDEX idx_ai_audit_object ON ai_audit_log (object_type, object_id, created_at);

-- Four initial text-model price groups supplied for the first channel-pool release.
INSERT INTO ai_model (id, model_code, display_name, provider_code, model_type, billing_mode, unit, status, version_no, created_at, updated_at) VALUES
('00000000-0000-0000-0007-000000000001','deepseek-chat','DeepSeek Chat','DEEPSEEK','TEXT','TOKEN','MILLION_TOKENS','ACTIVE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('00000000-0000-0000-0007-000000000002','deepseek-reasoner','DeepSeek Reasoner','DEEPSEEK','TEXT','TOKEN','MILLION_TOKENS','ACTIVE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('00000000-0000-0000-0007-000000000003','qwen-plus','Qwen Plus','QWEN','TEXT','TOKEN','MILLION_TOKENS','ACTIVE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('00000000-0000-0000-0007-000000000004','qwen-max','Qwen Max','QWEN','TEXT','TOKEN','MILLION_TOKENS','ACTIVE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));

INSERT INTO ai_model_pricing (id,model_id,fixed_price,model_rate,cache_rate,completion_rate,currency,status,effective_from,effective_to,version_no,created_by,created_at) VALUES
('00000000-0000-0000-0007-100000000001','00000000-0000-0000-0007-000000000001',0.00000000,0.27000000,0.25000000,4.07407407,'USD','ACTIVE',CURRENT_TIMESTAMP(3),NULL,0,NULL,CURRENT_TIMESTAMP(3)),
('00000000-0000-0000-0007-100000000002','00000000-0000-0000-0007-000000000002',0.00000000,0.55000000,0.25000000,4.00000000,'USD','ACTIVE',CURRENT_TIMESTAMP(3),NULL,0,NULL,CURRENT_TIMESTAMP(3)),
('00000000-0000-0000-0007-100000000003','00000000-0000-0000-0007-000000000003',0.00000000,0.40000000,0.25000000,3.00000000,'USD','ACTIVE',CURRENT_TIMESTAMP(3),NULL,0,NULL,CURRENT_TIMESTAMP(3)),
('00000000-0000-0000-0007-100000000004','00000000-0000-0000-0007-000000000004',0.00000000,1.60000000,0.25000000,4.00000000,'USD','ACTIVE',CURRENT_TIMESTAMP(3),NULL,0,NULL,CURRENT_TIMESTAMP(3));
