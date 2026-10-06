-- Built-in resume templates rendered by the shared HTML renderer (docs/phase2/03 §5).
-- BuiltInTemplateSynchronizer upserts them from templates/builtin-manifests.json at startup,
-- retires the twelve resume-layout-v3 drafts and moves editable layouts to the mapped template.
ALTER TABLE resume_layout_templates ADD COLUMN builtin TINYINT NOT NULL DEFAULT 0;
-- Catalog order and the "recommended" badge; operators may change both, the synchronizer only seeds them.
ALTER TABLE resume_layout_templates ADD COLUMN sort_order INT NOT NULL DEFAULT 1000;
ALTER TABLE resume_layout_templates ADD COLUMN featured TINYINT NOT NULL DEFAULT 0;
-- Who produced the gate flags: ADMIN_EVIDENCE (publish workflow) or BUILTIN_CI (render regression in CI).
ALTER TABLE resume_layout_template_versions ADD COLUMN verification_source VARCHAR(32) NULL;

UPDATE resume_layout_template_versions SET verification_source = 'ADMIN_EVIDENCE'
WHERE status IN ('PUBLISHED', 'RETIRED') AND verification_source IS NULL;
