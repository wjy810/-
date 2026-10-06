ALTER TABLE career_ability_validations ADD COLUMN batch_id CHAR(36) NULL;
CREATE INDEX idx_career_validation_batch
    ON career_ability_validations(account_id, session_id, batch_id, created_at);
