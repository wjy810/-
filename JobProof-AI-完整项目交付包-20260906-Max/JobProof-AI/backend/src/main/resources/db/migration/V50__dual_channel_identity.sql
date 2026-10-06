ALTER TABLE accounts MODIFY COLUMN email VARCHAR(320) NULL;
ALTER TABLE accounts ADD COLUMN phone_e164 VARCHAR(20) NULL;
ALTER TABLE accounts ADD COLUMN email_verified_at DATETIME(3) NULL;
ALTER TABLE accounts ADD COLUMN phone_verified_at DATETIME(3) NULL;
ALTER TABLE accounts ADD COLUMN primary_channel VARCHAR(16) NULL;
ALTER TABLE accounts ADD COLUMN terms_version VARCHAR(64) NULL;
ALTER TABLE accounts ADD COLUMN terms_accepted_at DATETIME(3) NULL;
ALTER TABLE accounts ADD COLUMN privacy_version VARCHAR(64) NULL;
ALTER TABLE accounts ADD COLUMN privacy_accepted_at DATETIME(3) NULL;
ALTER TABLE accounts ADD CONSTRAINT uk_accounts_phone UNIQUE (phone_e164);
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_contact CHECK (email IS NOT NULL OR phone_e164 IS NOT NULL);

UPDATE accounts
SET email_verified_at = COALESCE(email_verified_at, created_at),
    primary_channel = COALESCE(primary_channel, 'EMAIL')
WHERE email IS NOT NULL;

ALTER TABLE contact_verification_challenges MODIFY COLUMN code_hash VARCHAR(64) NULL;
ALTER TABLE contact_verification_challenges ADD COLUMN verification_mode VARCHAR(32) NOT NULL DEFAULT 'LOCAL_CODE';
ALTER TABLE contact_verification_challenges ADD COLUMN provider_request_id VARCHAR(128) NULL;
ALTER TABLE contact_verification_challenges ADD COLUMN request_ip_hash VARCHAR(64) NULL;
ALTER TABLE contact_verification_challenges ADD COLUMN verification_token_expires_at DATETIME(3) NULL;

UPDATE contact_verification_challenges
SET verification_token_expires_at = expires_at
WHERE verification_token_hash IS NOT NULL;
