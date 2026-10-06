-- Product contraction: career library becomes the durable source of career facts and files.
-- Historical application/interview/review rows are copied to an immutable account archive
-- before the retired write-side tables are removed.

CREATE TABLE career_library_profiles (
    account_id CHAR(36) NOT NULL PRIMARY KEY,
    basics_json TEXT NOT NULL,
    intentions_json TEXT NOT NULL,
    preferences_json TEXT NOT NULL,
    summary_text TEXT NULL,
    snapshot_version INT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE career_library_records (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    organization VARCHAR(255) NULL,
    role_name VARCHAR(255) NULL,
    start_date VARCHAR(32) NULL,
    end_date VARCHAR(32) NULL,
    location VARCHAR(255) NULL,
    description_text TEXT NULL,
    core_outcome TEXT NULL,
    url VARCHAR(1024) NULL,
    payload_json TEXT NOT NULL,
    strength VARCHAR(32) NULL,
    pending_supplement TINYINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    confirmed TINYINT NOT NULL,
    sort_order INT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_career_records_account ON career_library_records (account_id, status, record_type, sort_order);
CREATE INDEX idx_career_records_source ON career_library_records (source_type, source_ref_id);

CREATE TABLE career_library_record_refs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    record_id CHAR(36) NOT NULL,
    resume_version_id CHAR(36) NOT NULL,
    active TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_record_ref UNIQUE (record_id, resume_version_id)
);
CREATE INDEX idx_career_record_ref_active ON career_library_record_refs (record_id, active);

CREATE TABLE career_library_files (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    private_file_id CHAR(36) NOT NULL,
    category VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NULL,
    scan_status VARCHAR(32) NOT NULL,
    scan_engine VARCHAR(64) NULL,
    scan_detail VARCHAR(255) NULL,
    preview_status VARCHAR(32) NOT NULL,
    preview_page_count INT NOT NULL,
    preview_error VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    archived_at DATETIME(3) NULL,
    CONSTRAINT uk_career_file_private UNIQUE (private_file_id)
);
CREATE INDEX idx_career_files_account ON career_library_files (account_id, status, category, updated_at);

CREATE TABLE career_library_file_previews (
    id CHAR(36) NOT NULL PRIMARY KEY,
    file_id CHAR(36) NOT NULL,
    page_number INT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width_px INT NOT NULL,
    height_px INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_career_file_preview UNIQUE (file_id, page_number)
);
CREATE INDEX idx_career_file_previews_file ON career_library_file_previews (file_id, page_number);

CREATE TABLE retired_career_history_records (
    id VARCHAR(80) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    source_id CHAR(36) NOT NULL,
    parent_source_id CHAR(36) NULL,
    secondary_parent_id CHAR(36) NULL,
    status VARCHAR(32) NULL,
    label VARCHAR(255) NULL,
    detail_text TEXT NULL,
    content_text TEXT NULL,
    payload_json_a TEXT NULL,
    payload_json_b TEXT NULL,
    reference_id_a CHAR(36) NULL,
    reference_id_b CHAR(36) NULL,
    reference_id_c CHAR(36) NULL,
    event_time DATETIME(3) NULL,
    sequence_no INT NOT NULL,
    archived_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_retired_history_account ON retired_career_history_records (account_id, event_time, sequence_no);

INSERT INTO career_library_profiles (
    account_id, basics_json, intentions_json, preferences_json, summary_text,
    snapshot_version, version_no, created_at, updated_at)
SELECT account_id, '{}', '{}', '{}', NULL, snapshot_version, version_no, created_at, updated_at
FROM profile_headers;

INSERT INTO career_library_profiles (
    account_id, basics_json, intentions_json, preferences_json, summary_text,
    snapshot_version, version_no, created_at, updated_at)
SELECT a.id, '{}', '{}', '{}', NULL, 0, 0, a.created_at, a.updated_at
FROM accounts a
WHERE NOT EXISTS (SELECT 1 FROM career_library_profiles p WHERE p.account_id = a.id);

INSERT INTO career_library_records (
    id, account_id, record_type, title, organization, role_name, start_date, end_date,
    location, description_text, core_outcome, url, payload_json, strength,
    pending_supplement, source_type, source_ref_id, status, confirmed, sort_order,
    version_no, created_at, updated_at, archived_at)
SELECT id, account_id, 'PROFILE_FACT', field_key, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, value_json, NULL,
    0, 'MIGRATED_PROFILE', id, 'ACTIVE', 1, 0,
    0, updated_at, updated_at, NULL
FROM profile_facts;

INSERT INTO career_library_records (
    id, account_id, record_type, title, organization, role_name, start_date, end_date,
    location, description_text, core_outcome, url, payload_json, strength,
    pending_supplement, source_type, source_ref_id, status, confirmed, sort_order,
    version_no, created_at, updated_at, archived_at)
SELECT id, account_id, 'ACHIEVEMENT', title, NULL, NULL, NULL, NULL,
    NULL, body, core_outcome, url, '{}', strength,
    pending_supplement, 'MIGRATED_EVIDENCE', id, status, 1, 0,
    version_no, created_at, updated_at, archived_at
FROM evidences;

INSERT INTO career_library_files (
    id, account_id, private_file_id, category, display_name, original_filename,
    content_type, size_bytes, sha256, scan_status, scan_engine, scan_detail,
    preview_status, preview_page_count, preview_error, status, version_no,
    created_at, updated_at, archived_at)
SELECT e.id, e.account_id, e.file_id, 'PROOF', e.title, 'migrated-attachment',
    f.content_type, f.size_bytes, NULL, 'LEGACY_ACCEPTED', NULL, 'MIGRATED_WITHOUT_RESCAN',
    'PENDING', 0, NULL, e.status, 0,
    e.created_at, e.updated_at, e.archived_at
FROM evidences e
JOIN private_files f ON f.id = e.file_id
WHERE e.file_id IS NOT NULL;

INSERT INTO career_library_record_refs (id, record_id, resume_version_id, active, created_at)
SELECT id, evidence_id, resume_version_id, active, created_at
FROM evidence_references;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('APPLICATION:', id), account_id, 'APPLICATION', id, NULL, NULL,
    status, channel, note, company_alias, main_stack_json, NULL,
    resume_version_id, job_version_id, job_id, COALESCE(applied_at, created_at), 0, CURRENT_TIMESTAMP
FROM applications;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('APPLICATION_EVENT:', e.id), e.actor_account_id, 'APPLICATION_EVENT', e.id, e.application_id, NULL,
    e.to_status, e.action, e.reason, e.source, e.from_status, e.to_status,
    NULL, NULL, NULL, e.created_at, 1, CURRENT_TIMESTAMP
FROM application_stage_events e;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('INTERVIEW:', id), account_id, 'INTERVIEW', id, application_id, NULL,
    status, COALESCE(custom_name, type), note, timezone, result, NULL,
    NULL, NULL, NULL, COALESCE(scheduled_at, created_at), 2, CURRENT_TIMESTAMP
FROM interview_rounds;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('REVIEW:', id), account_id, 'REVIEW', id, application_id, interview_round_id,
    status, input_source, failure_reason, input_text, questions_json, answers_json,
    resume_version_id, job_version_id, analyze_task_id, created_at, 3, CURRENT_TIMESTAMP
FROM review_reports;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('REVIEW_SUGGESTION:', s.id), s.account_id, 'REVIEW_SUGGESTION', s.id, s.report_id, NULL,
    s.status, NULL, NULL, s.text, NULL, NULL,
    NULL, NULL, NULL, s.created_at, 4 + s.ordinal, CURRENT_TIMESTAMP
FROM review_suggestions s;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('NOTIFICATION:', id), account_id, 'NOTIFICATION', id, NULL, NULL,
    status, title, body, type, NULL, NULL,
    NULL, NULL, NULL, created_at, 100, CURRENT_TIMESTAMP
FROM notifications
WHERE type IN ('APPLICATION_STAGE', 'INTERVIEW_REMINDER');

UPDATE resume_versions SET status = 'FROZEN', version_no = version_no + 1, updated_at = CURRENT_TIMESTAMP
WHERE status = 'BOUND';

DELETE FROM notifications WHERE type IN ('APPLICATION_STAGE', 'INTERVIEW_REMINDER');

DROP TABLE review_suggestions;
DROP TABLE review_reports;
DROP TABLE interview_rounds;
DROP TABLE application_stage_events;
DROP TABLE applications;
DROP TABLE evidence_references;
DROP TABLE evidences;
DROP TABLE profile_candidates;
DROP TABLE profile_facts;
DROP TABLE profile_headers;
