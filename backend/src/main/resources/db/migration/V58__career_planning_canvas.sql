-- Immutable career ability canvas foundation. AI proposals and learning plans follow in later slices.

CREATE TABLE canvas_versions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    goal_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    parent_version_id CHAR(36) NULL,
    reason_code VARCHAR(48) NOT NULL,
    graph_hash CHAR(64) NOT NULL,
    created_by VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_canvas_version UNIQUE(goal_id, version_no)
);
CREATE INDEX idx_career_canvas_goal ON canvas_versions(goal_id, version_no);

CREATE TABLE canvas_nodes (
    id CHAR(36) NOT NULL PRIMARY KEY,
    version_id CHAR(36) NOT NULL,
    logical_node_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    node_type VARCHAR(24) NOT NULL,
    node_status VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    detail_json TEXT NOT NULL,
    source_refs_json TEXT NOT NULL,
    position_x INT NOT NULL,
    position_y INT NOT NULL,
    locked TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_canvas_logical_node UNIQUE(version_id, logical_node_id)
);
CREATE INDEX idx_career_canvas_node_version ON canvas_nodes(version_id, sort_order);

CREATE TABLE node_relations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    version_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    from_logical_id CHAR(36) NOT NULL,
    to_logical_id CHAR(36) NOT NULL,
    relation_type VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_canvas_relation UNIQUE(version_id, from_logical_id, to_logical_id, relation_type)
);
CREATE INDEX idx_career_canvas_relation_version ON node_relations(version_id, relation_type);
