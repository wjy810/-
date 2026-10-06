UPDATE job_match_clarifications
SET status = 'DEFERRED'
WHERE status = 'ANSWERED'
  AND answer_code = '暂不确认';
