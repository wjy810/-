CREATE TABLE ai_charge_quotes (
 id CHAR(36) PRIMARY KEY, account_id CHAR(36) NOT NULL, operation_code VARCHAR(64) NOT NULL,
 units INT NOT NULL, unit_price DECIMAL(19,8) NOT NULL, price_version INT NOT NULL,
 payment_source VARCHAR(16) NOT NULL, personal_channel_id CHAR(36) NULL,
 request_path VARCHAR(512) NOT NULL, payload_hash CHAR(64) NOT NULL, payment_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 reservation_id CHAR(36) NULL, expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_charge_quote_account ON ai_charge_quotes(account_id,created_at);
ALTER TABLE ai_quota_reservations ADD COLUMN payment_source VARCHAR(16) NOT NULL DEFAULT 'QUOTA';
ALTER TABLE ai_quota_reservations ADD COLUMN quota_account_id CHAR(36) NULL;
ALTER TABLE ai_quota_reservations ADD COLUMN wallet_hold_id CHAR(36) NULL;
ALTER TABLE ai_quota_reservations ADD COLUMN unit_price DECIMAL(19,8) NOT NULL DEFAULT 0;
ALTER TABLE ai_quota_reservations ADD COLUMN quote_id CHAR(36) NULL;
ALTER TABLE ai_quota_reservations ADD COLUMN personal_channel_id CHAR(36) NULL;
UPDATE ai_quota_reservations SET quota_account_id=(SELECT q.id FROM ai_quota_accounts q
 WHERE q.account_id=ai_quota_reservations.account_id AND q.period_key=CONCAT(EXTRACT(YEAR FROM ai_quota_reservations.created_at),'-',LPAD(EXTRACT(MONTH FROM ai_quota_reservations.created_at),2,'0')));
CREATE TABLE ai_charge_attempts (
 id CHAR(36) PRIMARY KEY, reservation_id CHAR(36) NOT NULL, account_id CHAR(36) NOT NULL,
 channel_id CHAR(36) NULL, model_code VARCHAR(255) NULL, status VARCHAR(24) NOT NULL,
 input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
 failure_code VARCHAR(100) NULL, created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_charge_attempt_reservation ON ai_charge_attempts(reservation_id,created_at);
ALTER TABLE ai_usage_record MODIFY COLUMN model_id CHAR(36) NULL;
ALTER TABLE ai_usage_record MODIFY COLUMN channel_id CHAR(36) NULL;
ALTER TABLE ai_usage_record ADD COLUMN model_code VARCHAR(255) NULL;
ALTER TABLE ai_usage_record ADD COLUMN payment_source VARCHAR(16) NOT NULL DEFAULT 'QUOTA';
ALTER TABLE ai_usage_record ADD COLUMN operation_code VARCHAR(64) NULL;
ALTER TABLE async_tasks ADD COLUMN ai_quote_id CHAR(36) NULL;
ALTER TABLE async_tasks ADD COLUMN ai_request_path VARCHAR(512) NULL;
ALTER TABLE async_tasks ADD COLUMN ai_payload_hash CHAR(64) NULL;
ALTER TABLE async_tasks ADD COLUMN ai_payment_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE async_tasks ADD COLUMN ai_reservation_id CHAR(36) NULL;
ALTER TABLE async_tasks ADD COLUMN ai_personal_channel_id CHAR(36) NULL;
CREATE TABLE ai_channel_preferences (account_id CHAR(36) PRIMARY KEY, personal_channel_id CHAR(36) NULL, version_no INT NOT NULL DEFAULT 0);
CREATE TABLE ai_charge_successes (reservation_id CHAR(36) NOT NULL, unit_key VARCHAR(100) NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(reservation_id,unit_key));
INSERT INTO commerce_prices(code,name,amount,currency,days,enabled,version) VALUES
 ('RESUME_CHAT','简历生成与修改',NULL,'CNY',0,FALSE,0),
 ('RESUME_DESCRIPTION','经历描述建议',NULL,'CNY',0,FALSE,0),
 ('RESUME_SUMMARY','个人总结建议',NULL,'CNY',0,FALSE,0),
 ('RESUME_SKILL_NAMES','技能名称建议',NULL,'CNY',0,FALSE,0),
 ('RESUME_SKILL_DETAILS','技能内容建议',NULL,'CNY',0,FALSE,0),
 ('RESUME_CERTIFICATE_DETAILS','证书内容建议',NULL,'CNY',0,FALSE,0),
 ('RESUME_HONOR_DETAILS','荣誉内容建议',NULL,'CNY',0,FALSE,0),
 ('RESUME_CREDENTIAL_RECOMMENDATIONS','证书与荣誉推荐',NULL,'CNY',0,FALSE,0),
 ('RESUME_TRANSLATION','简历翻译',NULL,'CNY',0,FALSE,0),
 ('RESUME_CANDIDATE','简历字段优化',NULL,'CNY',0,FALSE,0),
 ('CAREER_RECORD_OPTIMIZE','经历优化',NULL,'CNY',0,FALSE,0),
 ('JOB_MATCH_ANALYSIS','岗位匹配分析',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_INTERVIEW','职业访谈',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_RECOMMENDATIONS','职业方向推荐',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_CANVAS','能力画布生成',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_CANVAS_PROPOSAL','能力画布推演',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_VALIDATION','能力验证',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_VALIDATION_BATCH','批量能力验证',NULL,'CNY',0,FALSE,0),
 ('CAREER_PLANNING_QUIZ','能力测验出题',NULL,'CNY',0,FALSE,0),
 ('MOCK_INTERVIEW_QUESTIONS','模拟面试出题',NULL,'CNY',0,FALSE,0),
 ('MOCK_INTERVIEW_EVALUATE','模拟面试回答评估',NULL,'CNY',0,FALSE,0);
