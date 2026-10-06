UPDATE resume_masters SET status = 'READY_TO_EXPORT', version_no = version_no + 1, updated_at = CURRENT_TIMESTAMP
WHERE status = 'READY_TO_APPLY';
