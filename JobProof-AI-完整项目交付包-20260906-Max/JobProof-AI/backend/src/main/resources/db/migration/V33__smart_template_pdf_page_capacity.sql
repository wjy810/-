-- A recommended one-page resume is not a hard one-page document limit. Long,
-- confirmed nine-section content may continue to page two instead of clipping
-- or making PDF export unavailable.
UPDATE resume_layout_template_versions
SET definition_json = REPLACE(definition_json, '"maxPages":1', '"maxPages":2'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE renderer_protocol = 'resume-layout-v3'
  AND template_id IN (
    'rlt-b-ats-minimal-v1',
    'rlt-b-tech-single-v1',
    'rlt-b-tech-double-v1',
    'rlt-b-campus-v1',
    'rlt-b-career-pro-v1',
    'rlt-b-consulting-v1',
    'rlt-b-finance-v1',
    'rlt-b-product-ops-v1',
    'rlt-b-education-research-v1',
    'rlt-b-english-single-v1',
    'rlt-b-cn-table-v1',
    'rlt-b-qa-data-v1'
  )
  AND definition_json LIKE '%"maxPages":1%';
