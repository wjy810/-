INSERT INTO resume_layout_template_versions(
    id, template_id, revision_no, status, renderer_protocol, definition_json, thumbnail_uri,
    authorization_verified, security_verified, render_verified, word_verified, wps_verified, ats_verified,
    test_report_json, version_no, published_at, retired_at, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000901',
    'rlt-b-ats-minimal-v1',
    1,
    'DRAFT',
    'resume-layout-v1',
    '{"page":{"maxPages":1,"capacityUnits":3600},"columns":[{"id":"main","widthPercent":100,"slotKeys":["summary","education","experience","projects","skills","certificates"]}],"slots":[{"key":"summary","order":10,"capacityUnits":420,"repeatable":false,"hideWhenEmpty":true},{"key":"education","order":20,"capacityUnits":520,"repeatable":true,"hideWhenEmpty":true},{"key":"experience","order":30,"capacityUnits":900,"repeatable":true,"hideWhenEmpty":true},{"key":"projects","order":40,"capacityUnits":900,"repeatable":true,"hideWhenEmpty":true},{"key":"skills","order":50,"capacityUnits":520,"repeatable":true,"hideWhenEmpty":true},{"key":"certificates","order":60,"capacityUnits":340,"repeatable":true,"hideWhenEmpty":true}],"tokens":{"pageSize":"A4","fontFamily":"Noto Sans CJK SC, Microsoft YaHei, sans-serif","fontSize":"10.5pt","lineHeight":"1.45","accent.MONO":"#1F2937","accent.BLUE":"#175CD3"}}',
    NULL,
    0, 1, 0, 0, 0, 0,
    '{"scope":"DEV_TEST_ONLY","notice":"独立受控版式；未完成生产授权、渲染、Word、WPS 与 ATS 门禁"}',
    0, NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
);

INSERT INTO resume_template_slots(
    id, template_version_id, slot_key, display_order, capacity_units,
    repeatable, hide_when_empty, overflow_strategy, token_json
) VALUES
('00000000-0000-0000-0000-000000000902','00000000-0000-0000-0000-000000000901','summary',10,420,0,1,'BLOCK','{"label":"个人简介"}'),
('00000000-0000-0000-0000-000000000903','00000000-0000-0000-0000-000000000901','education',20,520,1,1,'BLOCK','{"label":"教育经历"}'),
('00000000-0000-0000-0000-000000000904','00000000-0000-0000-0000-000000000901','experience',30,900,1,1,'BLOCK','{"label":"工作经历"}'),
('00000000-0000-0000-0000-000000000905','00000000-0000-0000-0000-000000000901','projects',40,900,1,1,'BLOCK','{"label":"项目经历"}'),
('00000000-0000-0000-0000-000000000906','00000000-0000-0000-0000-000000000901','skills',50,520,1,1,'BLOCK','{"label":"专业技能"}'),
('00000000-0000-0000-0000-000000000907','00000000-0000-0000-0000-000000000901','certificates',60,340,1,1,'BLOCK','{"label":"证书与资质"}');
