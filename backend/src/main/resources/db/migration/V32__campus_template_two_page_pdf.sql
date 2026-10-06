-- The campus template remains a one-page recommendation, but complete student
-- resumes may use a second continuation page instead of becoming unexportable.
UPDATE resume_layout_template_versions
SET definition_json = REPLACE(definition_json, '"maxPages":1', '"maxPages":2'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE template_id = 'rlt-b-campus-v1'
  AND renderer_protocol = 'resume-layout-v3'
  AND definition_json LIKE '%"maxPages":1%';
