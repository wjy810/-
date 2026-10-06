-- Standalone AI mock interview training. This is intentionally independent from the
-- retired application/interview/review workflow and the former ai_mock_interviews tables.

CREATE TABLE mock_interview_drafts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    step_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_mock_interview_drafts_account
    ON mock_interview_drafts (account_id, status, updated_at);

CREATE TABLE mock_interview_sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    draft_id CHAR(36) NULL,
    title VARCHAR(180) NOT NULL,
    position_name VARCHAR(160) NOT NULL,
    company_name VARCHAR(160) NULL,
    mode VARCHAR(16) NOT NULL,
    interview_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    duration_minutes INT NOT NULL,
    question_count INT NOT NULL,
    language_code VARCHAR(16) NOT NULL,
    feedback_mode VARCHAR(24) NOT NULL,
    follow_up_enabled TINYINT(1) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_question_index INT NOT NULL,
    elapsed_seconds INT NOT NULL,
    resume_snapshot_json TEXT NOT NULL,
    jd_snapshot_json TEXT NOT NULL,
    materials_snapshot_json TEXT NOT NULL,
    settings_snapshot_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NULL,
    paused_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL
);
CREATE INDEX idx_mock_interview_sessions_account
    ON mock_interview_sessions (account_id, status, updated_at);

CREATE TABLE mock_interview_questions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    order_no INT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    prompt_text TEXT NOT NULL,
    source_label VARCHAR(255) NULL,
    source_refs_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_mock_interview_question_order UNIQUE (session_id, order_no)
);
CREATE INDEX idx_mock_interview_questions_session
    ON mock_interview_questions (session_id, order_no);

CREATE TABLE mock_interview_answers (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    question_id CHAR(36) NOT NULL,
    answer_mode VARCHAR(16) NOT NULL,
    answer_text TEXT NOT NULL,
    transcript_text TEXT NULL,
    status VARCHAR(24) NOT NULL,
    score_json TEXT NOT NULL,
    feedback_json TEXT NOT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    submitted_at DATETIME(3) NULL,
    CONSTRAINT uk_mock_interview_answer_question UNIQUE (session_id, question_id)
);
CREATE INDEX idx_mock_interview_answers_session
    ON mock_interview_answers (session_id, status, updated_at);

CREATE TABLE mock_interview_audio_chunks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    question_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    private_file_id CHAR(36) NOT NULL,
    duration_ms INT NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_mock_interview_audio_sequence UNIQUE (session_id, question_id, sequence_no)
);

CREATE TABLE mock_interview_reports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    overall_score INT NOT NULL,
    dimensions_json TEXT NOT NULL,
    summary_json TEXT NOT NULL,
    recommendations_json TEXT NOT NULL,
    generated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_mock_interview_report_session UNIQUE (session_id)
);
CREATE INDEX idx_mock_interview_reports_account
    ON mock_interview_reports (account_id, generated_at);
