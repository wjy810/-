-- 模拟面试计时 (docs/phase2/09): the last moment the interview page reported itself active. A session whose page
-- stopped reporting (tab closed, device asleep, network gone) is paused at that moment, so the hours that
-- followed are not counted as interview time.
ALTER TABLE mock_interview_sessions ADD COLUMN last_active_at DATETIME(3) NULL;
UPDATE mock_interview_sessions SET last_active_at = COALESCE(resumed_at, updated_at);
