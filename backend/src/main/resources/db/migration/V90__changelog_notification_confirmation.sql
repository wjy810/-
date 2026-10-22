ALTER TABLE changelog_releases ADD COLUMN notification_confirmed_version INT NULL;
-- Existing scheduled releases have never been explicitly confirmed in the new workflow.
UPDATE changelog_releases SET send_notification=FALSE WHERE status='SCHEDULED';
CREATE INDEX idx_audit_event_time ON audit_events (created_at, id);
