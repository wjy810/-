ALTER TABLE resume_candidates ADD COLUMN candidate_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE resume_candidates ADD COLUMN ai_action VARCHAR(32) NULL;
ALTER TABLE resume_candidates ADD COLUMN reason_text VARCHAR(2048) NULL;
ALTER TABLE resume_candidates ADD COLUMN diff_json TEXT NULL;
ALTER TABLE resume_candidates ADD COLUMN source_facts_json TEXT NULL;
ALTER TABLE resume_candidates ADD COLUMN generation_metadata_json TEXT NULL;
