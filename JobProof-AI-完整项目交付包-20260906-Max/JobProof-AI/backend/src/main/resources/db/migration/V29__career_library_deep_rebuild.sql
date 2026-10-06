-- Career library deep rebuild: asynchronous processing, folders and AI review candidates.

CREATE TABLE career_library_file_folders (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL,
    CONSTRAINT uk_career_file_folder_name UNIQUE (account_id, name)
);
CREATE INDEX idx_career_file_folders_account
    ON career_library_file_folders (account_id, status, updated_at);

ALTER TABLE career_library_files
    ADD COLUMN folder_id CHAR(36) NULL;
ALTER TABLE career_library_files
    ADD COLUMN processing_task_id CHAR(36) NULL;
ALTER TABLE career_library_files
    ADD COLUMN processing_status VARCHAR(32) NOT NULL DEFAULT 'READY';
ALTER TABLE career_library_files
    ADD COLUMN processing_attempts INT NOT NULL DEFAULT 0;

CREATE INDEX idx_career_files_folder
    ON career_library_files (account_id, folder_id, status, updated_at);
CREATE INDEX idx_career_files_processing
    ON career_library_files (processing_status, updated_at);

ALTER TABLE career_library_profiles
    ADD COLUMN avatar_file_id CHAR(36) NULL;

CREATE TABLE career_library_ai_candidates (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    record_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    proposed_json TEXT NOT NULL,
    diff_json TEXT NOT NULL,
    source_refs_json TEXT NOT NULL,
    model_name VARCHAR(128) NULL,
    response_hash CHAR(64) NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL
);
CREATE INDEX idx_career_ai_candidates_record
    ON career_library_ai_candidates (account_id, record_id, status, created_at);
