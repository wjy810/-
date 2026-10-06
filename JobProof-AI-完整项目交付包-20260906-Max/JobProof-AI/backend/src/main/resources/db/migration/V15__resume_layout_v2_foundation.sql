-- 仅升级尚未被运营编辑的开发演示草稿；已冻结的 v1 快照继续由兼容渲染器读取。
UPDATE resume_layout_template_versions
SET renderer_protocol = 'resume-layout-v2',
    definition_json = '{"page":{"maxPages":1,"capacityUnits":3600,"marginXPt":50,"marginTopPt":48,"marginBottomPt":46},"columns":[{"id":"main","widthPercent":100,"slotKeys":["summary","education","experience","projects","skills","certificates"],"tone":"PLAIN"}],"slots":[{"key":"summary","order":10,"capacityUnits":420,"repeatable":false,"hideWhenEmpty":true,"label":"个人简介","headingStyle":"RULE"},{"key":"education","order":20,"capacityUnits":520,"repeatable":true,"hideWhenEmpty":true,"label":"教育经历","headingStyle":"RULE"},{"key":"experience","order":30,"capacityUnits":900,"repeatable":true,"hideWhenEmpty":true,"label":"工作经历","headingStyle":"RULE"},{"key":"projects","order":40,"capacityUnits":900,"repeatable":true,"hideWhenEmpty":true,"label":"项目经历","headingStyle":"RULE"},{"key":"skills","order":50,"capacityUnits":520,"repeatable":true,"hideWhenEmpty":true,"label":"专业技能","headingStyle":"RULE"},{"key":"certificates","order":60,"capacityUnits":340,"repeatable":true,"hideWhenEmpty":true,"label":"证书与资质","headingStyle":"RULE"}],"tokens":{"fontFamily":"Noto Sans SC","accent.MONO":"#1F2937","surface.MONO":"#F3F4F6","accent.BLUE":"#175CD3","surface.BLUE":"#EEF4FF","body":"#1F2937","muted":"#667085"},"visual":{"headerStyle":"MINIMAL","sectionStyle":"RULE","subtitle":"结构化简历 · ATS 单栏","showMark":false,"density":"COMPACT"}}',
    version_no = version_no + 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = '00000000-0000-0000-0000-000000000901'
  AND status = 'DRAFT'
  AND renderer_protocol = 'resume-layout-v1'
  AND version_no = 0;

UPDATE resume_template_slots
SET token_json = CASE slot_key
    WHEN 'summary' THEN '{"label":"个人简介","headingStyle":"RULE"}'
    WHEN 'education' THEN '{"label":"教育经历","headingStyle":"RULE"}'
    WHEN 'experience' THEN '{"label":"工作经历","headingStyle":"RULE"}'
    WHEN 'projects' THEN '{"label":"项目经历","headingStyle":"RULE"}'
    WHEN 'skills' THEN '{"label":"专业技能","headingStyle":"RULE"}'
    WHEN 'certificates' THEN '{"label":"证书与资质","headingStyle":"RULE"}'
    ELSE token_json
END
WHERE template_version_id = '00000000-0000-0000-0000-000000000901'
  AND EXISTS (
      SELECT 1
      FROM resume_layout_template_versions v
      WHERE v.id = '00000000-0000-0000-0000-000000000901'
        AND v.renderer_protocol = 'resume-layout-v2'
  );
