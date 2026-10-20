-- 模拟面试真追问 (docs/phase2/08): a follow-up asked during the interview is a question of its own, placed right
-- after the question it follows up on and pointing at that original (root) question.
ALTER TABLE mock_interview_questions ADD COLUMN parent_question_id CHAR(36) NULL;
