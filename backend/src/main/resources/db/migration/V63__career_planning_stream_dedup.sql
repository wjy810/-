-- Stable operation keys prevent retries from emitting duplicate lifecycle events.

ALTER TABLE career_planning_stream_events
    ADD COLUMN dedupe_key VARCHAR(160) NULL;

CREATE UNIQUE INDEX uk_career_planning_event_dedupe
    ON career_planning_stream_events(session_id, dedupe_key);

