-- The product no longer exposes interview simulation. Preserve existing sessions in the
-- account archive, then remove the retired tables and reserve candidate source metadata.

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('AI_INTERVIEW:', id), account_id, 'AI_INTERVIEW', id, conversation_id, branch_id,
    status, mode, NULL, NULL, NULL, NULL,
    job_version_id, NULL, NULL, created_at, 200, CURRENT_TIMESTAMP
FROM ai_mock_interviews;

INSERT INTO retired_career_history_records (
    id, account_id, record_type, source_id, parent_source_id, secondary_parent_id,
    status, label, detail_text, content_text, payload_json_a, payload_json_b,
    reference_id_a, reference_id_b, reference_id_c, event_time, sequence_no, archived_at)
SELECT CONCAT('AI_INTERVIEW_TURN:', t.id), i.account_id, 'AI_INTERVIEW_TURN', t.id, t.interview_id, i.conversation_id,
    t.status, t.question_text, NULL, t.answer_text, t.feedback_json, NULL,
    NULL, NULL, NULL, t.created_at, 200 + t.turn_no, CURRENT_TIMESTAMP
FROM ai_mock_interview_turns t
JOIN ai_mock_interviews i ON i.id = t.interview_id;

DROP TABLE ai_mock_interview_turns;
DROP TABLE ai_mock_interviews;

ALTER TABLE resume_candidates ADD COLUMN career_library_snapshot_version INT NULL;
ALTER TABLE resume_candidates ADD COLUMN career_library_sources_json TEXT NULL;
