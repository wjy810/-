-- Outbox delivery state (docs/03 §5.6): failed events are retried with backoff instead of being
-- marked published, and give up as DEAD after a bounded number of attempts.
ALTER TABLE outbox_events ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN attempts INT NOT NULL DEFAULT 0;
-- Also used as the processing lease: a claimed event becomes due again if its worker dies.
ALTER TABLE outbox_events ADD COLUMN next_attempt_at DATETIME(3) NULL;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(500) NULL;

UPDATE outbox_events SET status = 'PUBLISHED' WHERE published_at IS NOT NULL;

CREATE INDEX idx_outbox_due ON outbox_events (status, next_attempt_at, created_at);
