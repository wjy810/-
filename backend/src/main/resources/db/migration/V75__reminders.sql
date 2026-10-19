-- Reminders (docs/phase2/01 §2, 02 §2): which next step and which membership expiry were already announced,
-- so a periodic sweep sends each reminder once, on whichever instance claims it first.

ALTER TABLE job_applications ADD COLUMN next_step_reminded_for DATETIME(3) NULL;
CREATE INDEX idx_job_applications_next_step ON job_applications(next_step_at);

ALTER TABLE account_memberships ADD COLUMN expiry_notice VARCHAR(8) NULL;
ALTER TABLE account_memberships ADD COLUMN expiry_notice_for DATETIME(3) NULL;
CREATE INDEX idx_account_memberships_expires ON account_memberships(expires_at);
