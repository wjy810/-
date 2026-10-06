-- Mock interview without fabricated numbers (docs/phase2/00 BE-1, H-2, H-5).
-- Reports may have no score: answers are only scored by the AI model.
ALTER TABLE mock_interview_reports MODIFY COLUMN overall_score INT NULL;
ALTER TABLE mock_interview_reports ADD COLUMN evaluated_count INT NOT NULL DEFAULT 0;
-- Start of the current active stretch; elapsed_seconds accumulates the finished stretches.
ALTER TABLE mock_interview_sessions ADD COLUMN resumed_at DATETIME(3) NULL;
UPDATE mock_interview_sessions SET resumed_at = started_at
WHERE status NOT IN ('PAUSED', 'COMPLETED', 'ABANDONED');

-- Scores computed from character and paragraph counts are not evaluations: clear them and their
-- derived feedback, and drop reports built on them (they are regenerated from what remains).
DELETE FROM mock_interview_reports WHERE session_id IN (
    SELECT DISTINCT session_id FROM mock_interview_answers WHERE feedback_json LIKE '%"BASIC_RULES"%');
UPDATE mock_interview_answers
SET score_json = '{}',
    feedback_json = '{"generationMode":"PENDING","modelNotice":"提交时 AI 通道不可用，本题尚未评估。"}'
WHERE feedback_json LIKE '%"BASIC_RULES"%';
