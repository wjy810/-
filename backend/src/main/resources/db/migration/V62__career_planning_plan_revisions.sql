-- Immutable learning-plan history. Mutable plan/task tables remain the current
-- projection; every accepted change appends a canonical snapshot here.

CREATE TABLE career_learning_plan_revisions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    plan_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    revision_no INT NOT NULL,
    parent_revision_id CHAR(36) NULL,
    restored_from_revision_id CHAR(36) NULL,
    reason_code VARCHAR(64) NOT NULL,
    plan_hash CHAR(64) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_by VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_plan_revision UNIQUE(plan_id, revision_no)
);

CREATE INDEX idx_career_plan_revision_history
    ON career_learning_plan_revisions(plan_id, revision_no);

ALTER TABLE career_learning_plans
    ADD COLUMN current_revision_id CHAR(36) NULL;

