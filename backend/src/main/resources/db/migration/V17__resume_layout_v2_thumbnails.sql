-- Bind only known JobProof draft revisions. Existing operator-provided thumbnails are never overwritten.
UPDATE resume_layout_template_versions
SET thumbnail_uri = CASE template_id
        WHEN 'rlt-b-ats-minimal-v1' THEN '/resume-template-thumbnails/rlt-b-ats-minimal-v1-r1.png'
        WHEN 'rlt-b-tech-single-v1' THEN '/resume-template-thumbnails/rlt-b-tech-single-v1-r1.png'
        WHEN 'rlt-b-tech-double-v1' THEN '/resume-template-thumbnails/rlt-b-tech-double-v1-r1.png'
        WHEN 'rlt-b-campus-v1' THEN '/resume-template-thumbnails/rlt-b-campus-v1-r1.png'
        WHEN 'rlt-b-career-pro-v1' THEN '/resume-template-thumbnails/rlt-b-career-pro-v1-r1.png'
        WHEN 'rlt-b-consulting-v1' THEN '/resume-template-thumbnails/rlt-b-consulting-v1-r1.png'
        WHEN 'rlt-b-finance-v1' THEN '/resume-template-thumbnails/rlt-b-finance-v1-r1.png'
        WHEN 'rlt-b-product-ops-v1' THEN '/resume-template-thumbnails/rlt-b-product-ops-v1-r1.png'
        WHEN 'rlt-b-education-research-v1' THEN '/resume-template-thumbnails/rlt-b-education-research-v1-r1.png'
        WHEN 'rlt-b-english-single-v1' THEN '/resume-template-thumbnails/rlt-b-english-single-v1-r1.png'
        WHEN 'rlt-b-cn-table-v1' THEN '/resume-template-thumbnails/rlt-b-cn-table-v1-r1.png'
        WHEN 'rlt-b-qa-data-v1' THEN '/resume-template-thumbnails/rlt-b-qa-data-v1-r1.png'
        ELSE thumbnail_uri
    END,
    version_no = version_no + 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE template_id IN (
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
  AND revision_no = 1
  AND status = 'DRAFT'
  AND thumbnail_uri IS NULL;
