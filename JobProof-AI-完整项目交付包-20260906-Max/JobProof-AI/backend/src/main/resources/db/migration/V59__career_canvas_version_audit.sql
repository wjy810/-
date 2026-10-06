-- Complete the immutable career-canvas audit envelope used by generation and user edits.

ALTER TABLE canvas_versions ADD COLUMN change_summary VARCHAR(512) NULL;
ALTER TABLE canvas_versions ADD COLUMN prompt_version VARCHAR(96) NULL;
ALTER TABLE canvas_versions ADD COLUMN schema_version VARCHAR(96) NULL;
ALTER TABLE canvas_versions ADD COLUMN model_code VARCHAR(128) NULL;
ALTER TABLE canvas_versions ADD COLUMN response_hash CHAR(64) NULL;

CREATE INDEX idx_career_canvas_parent ON canvas_versions(parent_version_id);
