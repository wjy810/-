-- Task notifications used to share one generic title ("任务状态更新"). New ones name the task and link
-- to its result (TaskNotificationCopy); this rewrites the generic rows already delivered the same way.
-- The task is found through the event id, which starts with the task id ("<taskId>:<status>").

UPDATE notifications SET title = '个人数据导出已完成', body = '导出文件已生成，请在有效期内下载；过期后需要重新导出。', action_path = COALESCE(action_path, '/account/data-rights')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'ACCOUNT_EXPORT');

UPDATE notifications SET title = '个人数据导出失败', body = '这次导出没有完成，可以在“数据与隐私”页面重新发起。', action_path = COALESCE(action_path, '/account/data-rights')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'ACCOUNT_EXPORT');

UPDATE notifications SET title = '简历 PDF 已生成', body = '回到这份简历即可下载 PDF。', action_path = COALESCE(action_path, '/resumes')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'RESUME_PDF_EXPORT');

UPDATE notifications SET title = '简历 PDF 生成失败', body = '这次没有生成 PDF，回到简历重新导出即可，内容不受影响。', action_path = COALESCE(action_path, '/resumes')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'RESUME_PDF_EXPORT');

UPDATE notifications SET title = '简历 Word 文档已生成', body = '回到这份简历即可下载 Word 文档。', action_path = COALESCE(action_path, '/resumes')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'RESUME_DOCX_EXPORT');

UPDATE notifications SET title = '简历 Word 文档生成失败', body = '这次没有生成 Word 文档，回到简历重新导出即可，内容不受影响。', action_path = COALESCE(action_path, '/resumes')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'RESUME_DOCX_EXPORT');

UPDATE notifications SET title = '资料文件已通过安全检查', body = '文件已可以预览，也能在简历和岗位匹配中引用。', action_path = COALESCE(action_path, '/career-library?view=files')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_FILE_PROCESS');

UPDATE notifications SET title = '资料文件没有通过处理', body = '文件未通过安全检查或无法生成预览，打开资料库查看原因并重试。', action_path = COALESCE(action_path, '/career-library?view=files')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_FILE_PROCESS');

UPDATE notifications SET title = '简历解析完成', body = '导入的内容已整理好，可以继续完善简历。', action_path = COALESCE(action_path, '/resumes')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'RESUME_IMPORT_PARSE');

UPDATE notifications SET title = '简历导入失败', body = '这份简历没有解析成功，可以换一个文件或直接粘贴文字重新导入。', action_path = COALESCE(action_path, '/resumes')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'RESUME_IMPORT_PARSE');

UPDATE notifications SET title = '岗位描述解析完成', body = '可以继续岗位匹配分析。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'JD_PARSE');

UPDATE notifications SET title = '岗位描述解析失败', body = '没有读懂这份岗位描述，可以补充内容后重新分析。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'JD_PARSE');

UPDATE notifications SET title = '岗位匹配报告已生成', body = '查看匹配度、证据和补强建议。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'JOB_MATCH');

UPDATE notifications SET title = '岗位匹配分析失败', body = '这次分析没有完成，打开后可以重新分析。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'JOB_MATCH');

UPDATE notifications SET title = '岗位匹配建议已生成', body = '补强建议已经整理好。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'MATCH_AI_ADVICE');

UPDATE notifications SET title = '岗位匹配建议生成失败', body = '这次没有生成建议，可以稍后在报告页重试。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'MATCH_AI_ADVICE');

UPDATE notifications SET title = '匹配报告已导出', body = '回到报告页即可下载导出的文件。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'JOB_MATCH_EXPORT');

UPDATE notifications SET title = '匹配报告导出失败', body = '这次导出没有完成，回到报告页重新导出即可。', action_path = COALESCE(action_path, '/job-match/history')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'JOB_MATCH_EXPORT');

UPDATE notifications SET title = '能力画布已生成', body = '打开职业规划查看新的能力画布。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_CANVAS');

UPDATE notifications SET title = '能力画布生成失败', body = '这次没有生成画布，打开职业规划可以重新生成。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_CANVAS');

UPDATE notifications SET title = '画布调整建议已生成', body = '确认后才会改动你的能力画布。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_CANVAS_PROPOSAL');

UPDATE notifications SET title = '画布调整建议生成失败', body = '这次没有生成调整建议，可以稍后重试。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_CANVAS_PROPOSAL');

UPDATE notifications SET title = '职业规划访谈已更新', body = '打开职业规划继续。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_INTERVIEW');

UPDATE notifications SET title = '职业规划访谈中断', body = '这一轮访谈没有完成，打开职业规划继续。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_INTERVIEW');

UPDATE notifications SET title = '职业方向推荐已生成', body = '打开职业规划查看推荐的方向和理由。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_RECOMMENDATIONS');

UPDATE notifications SET title = '职业方向推荐生成失败', body = '这次没有生成推荐，可以稍后重试。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_RECOMMENDATIONS');

UPDATE notifications SET title = '方向验证已完成', body = '验证结果已写回职业规划。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_VALIDATION_BATCH');

UPDATE notifications SET title = '方向验证没有完成', body = '这批验证没有完成，打开职业规划重新发起。', action_path = COALESCE(action_path, '/career-planning')
WHERE title = '任务状态更新' AND type = 'TASK_FAILED'
  AND EXISTS (SELECT 1 FROM async_tasks t WHERE t.id = SUBSTRING(notifications.event_id, 1, 36) AND t.task_type = 'CAREER_PLANNING_VALIDATION_BATCH');

UPDATE notifications SET title = '后台任务已完成', body = '你发起的一项后台任务已经完成。' WHERE title = '任务状态更新' AND type = 'TASK_COMPLETED';
UPDATE notifications SET title = '后台任务未完成', body = '你发起的一项后台任务没有完成，可以回到原页面重试。' WHERE title = '任务状态更新' AND type = 'TASK_FAILED';
