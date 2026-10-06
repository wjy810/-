-- Persist reproducible canvas generation settings and node-scoped AI inference context.

ALTER TABLE canvas_versions
    ADD COLUMN generation_config_json TEXT NULL;

ALTER TABLE career_canvas_ai_proposals
    ADD COLUMN proposal_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL_OPTIMIZATION';

ALTER TABLE career_canvas_ai_proposals
    ADD COLUMN target_logical_node_id CHAR(36) NULL;

ALTER TABLE career_canvas_ai_proposals
    ADD COLUMN direction_code VARCHAR(32) NULL;

ALTER TABLE career_canvas_ai_proposals
    ADD COLUMN depth_code VARCHAR(24) NULL;

ALTER TABLE career_canvas_ai_proposals
    ADD COLUMN task_id CHAR(36) NULL;

CREATE INDEX idx_canvas_ai_proposal_task
    ON career_canvas_ai_proposals(account_id, task_id);

