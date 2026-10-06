-- A seeker can maintain multiple independent career canvases. The primary flag
-- is presentation state only; every session keeps its own goal and history.

ALTER TABLE career_planning_sessions
    ADD COLUMN is_primary TINYINT NOT NULL DEFAULT 0;

CREATE INDEX idx_career_planning_canvas_overview
    ON career_planning_sessions(account_id, archived_at, status, is_primary, updated_at);

UPDATE career_planning_sessions current_session
SET is_primary = 1
WHERE current_session.status = 'ACTIVE'
  AND current_session.archived_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM career_planning_sessions newer_session
      WHERE newer_session.account_id = current_session.account_id
        AND newer_session.status = 'ACTIVE'
        AND newer_session.archived_at IS NULL
        AND (
            newer_session.updated_at > current_session.updated_at
            OR (newer_session.updated_at = current_session.updated_at AND newer_session.id > current_session.id)
        )
  );
