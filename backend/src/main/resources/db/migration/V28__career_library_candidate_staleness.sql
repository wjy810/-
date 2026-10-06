ALTER TABLE resume_candidates
    ADD COLUMN source_stale TINYINT(1) NOT NULL DEFAULT 0;

CREATE INDEX idx_resume_candidates_career_stale
    ON resume_candidates (account_id, status, career_library_snapshot_version);
