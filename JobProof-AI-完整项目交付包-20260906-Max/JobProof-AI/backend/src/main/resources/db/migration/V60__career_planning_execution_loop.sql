-- Career-planning M3/M4: reviewable AI diffs, executable plans, evidence,
-- validation, version restore and target-change audit.

CREATE TABLE career_canvas_ai_proposals (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    goal_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    base_version_id CHAR(36) NOT NULL,
    base_version_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    instruction_text VARCHAR(1000) NOT NULL,
    prompt_version VARCHAR(96) NOT NULL,
    schema_version VARCHAR(96) NOT NULL,
    model_code VARCHAR(128) NULL,
    response_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL
);
CREATE INDEX idx_canvas_ai_proposal_goal ON career_canvas_ai_proposals(goal_id, status, created_at);

CREATE TABLE career_canvas_ai_proposal_items (
    id CHAR(36) NOT NULL PRIMARY KEY,
    proposal_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    proposal_key VARCHAR(120) NOT NULL,
    operation_code VARCHAR(24) NOT NULL,
    target_logical_node_id CHAR(36) NULL,
    parent_logical_node_id VARCHAR(160) NULL,
    before_json TEXT NOT NULL,
    after_json TEXT NOT NULL,
    reason_text VARCHAR(1000) NOT NULL,
    source_refs_json TEXT NOT NULL,
    impact_node_ids_json TEXT NOT NULL,
    decision_status VARCHAR(24) NOT NULL,
    rejection_reason VARCHAR(1000) NULL,
    decided_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_canvas_ai_proposal_item_sequence UNIQUE(proposal_id, sequence_no),
    CONSTRAINT uk_canvas_ai_proposal_item_key UNIQUE(proposal_id, proposal_key)
);
CREATE INDEX idx_canvas_ai_proposal_item ON career_canvas_ai_proposal_items(proposal_id, decision_status);

CREATE TABLE career_learning_plans (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    goal_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    canvas_version_id CHAR(36) NOT NULL,
    canvas_version_no INT NOT NULL,
    duration_weeks INT NOT NULL,
    intensity VARCHAR(24) NOT NULL,
    weekly_hours INT NOT NULL,
    learning_days_json TEXT NOT NULL,
    start_date DATE NOT NULL,
    target_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    generation_method VARCHAR(24) NOT NULL,
    prompt_version VARCHAR(96) NULL,
    schema_version VARCHAR(96) NULL,
    model_code VARCHAR(128) NULL,
    response_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_career_learning_plan_goal ON career_learning_plans(goal_id, status, updated_at);

CREATE TABLE career_learning_plan_tasks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    plan_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    logical_node_id CHAR(36) NULL,
    task_type VARCHAR(24) NOT NULL,
    week_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description_text VARCHAR(2000) NULL,
    priority VARCHAR(16) NOT NULL,
    estimated_minutes INT NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    evidence_required TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL
);
CREATE INDEX idx_career_plan_task_week ON career_learning_plan_tasks(plan_id, week_no, sort_order);

CREATE TABLE career_learning_evidences (
    id CHAR(36) NOT NULL PRIMARY KEY,
    plan_id CHAR(36) NOT NULL,
    task_id CHAR(36) NULL,
    logical_node_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id CHAR(36) NULL,
    title VARCHAR(255) NOT NULL,
    note_text VARCHAR(2000) NULL,
    verification_status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_career_learning_evidence_node ON career_learning_evidences(account_id, logical_node_id, verification_status);

CREATE TABLE career_learning_weekly_reviews (
    id CHAR(36) NOT NULL PRIMARY KEY,
    plan_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    week_no INT NOT NULL,
    completed_summary VARCHAR(2000) NULL,
    blockers_text VARCHAR(2000) NULL,
    adjustment_text VARCHAR(2000) NULL,
    next_week_focus VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_weekly_review UNIQUE(plan_id, week_no)
);

CREATE TABLE career_ability_validations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    goal_id CHAR(36) NOT NULL,
    plan_id CHAR(36) NULL,
    account_id CHAR(36) NOT NULL,
    logical_node_id CHAR(36) NOT NULL,
    canvas_version_id CHAR(36) NOT NULL,
    method_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submission_json TEXT NOT NULL,
    score_json TEXT NOT NULL,
    result_code VARCHAR(24) NULL,
    feedback_json TEXT NOT NULL,
    user_confirmed TINYINT NOT NULL DEFAULT 0,
    prompt_version VARCHAR(96) NULL,
    schema_version VARCHAR(96) NULL,
    model_code VARCHAR(128) NULL,
    response_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    evaluated_at DATETIME(3) NULL,
    confirmed_at DATETIME(3) NULL
);
CREATE INDEX idx_career_validation_node ON career_ability_validations(account_id, logical_node_id, created_at);

CREATE TABLE career_ability_validation_evidences (
    validation_id CHAR(36) NOT NULL,
    evidence_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY(validation_id, evidence_id)
);

CREATE TABLE career_goal_transitions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    from_goal_id CHAR(36) NOT NULL,
    to_goal_id CHAR(36) NOT NULL,
    reused_node_ids_json TEXT NOT NULL,
    archived_plan_ids_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_career_goal_transition_session ON career_goal_transitions(session_id, created_at);
