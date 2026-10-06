ALTER TABLE resume_versions ADD COLUMN layout_instance_id CHAR(36) NULL;

CREATE TABLE resume_template_assets (
    id CHAR(36) PRIMARY KEY,
    source_name VARCHAR(255) NOT NULL,
    source_uri VARCHAR(1024) NOT NULL,
    license_status VARCHAR(32) NOT NULL,
    license_evidence VARCHAR(2048) NULL,
    file_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(1024) NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_resume_template_asset_hash (file_hash)
);

CREATE TABLE resume_layout_templates (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    family_name VARCHAR(128) NOT NULL,
    language_code VARCHAR(16) NOT NULL,
    recommended_pages VARCHAR(16) NOT NULL,
    ats_candidate_level VARCHAR(32) NOT NULL,
    photo_policy VARCHAR(16) NOT NULL,
    variants_json TEXT NOT NULL,
    tags_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE resume_layout_template_versions (
    id CHAR(36) PRIMARY KEY,
    template_id VARCHAR(64) NOT NULL,
    revision_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    renderer_protocol VARCHAR(32) NOT NULL,
    definition_json LONGTEXT NOT NULL,
    thumbnail_uri VARCHAR(1024) NULL,
    authorization_verified TINYINT NOT NULL,
    security_verified TINYINT NOT NULL,
    render_verified TINYINT NOT NULL,
    word_verified TINYINT NOT NULL,
    wps_verified TINYINT NOT NULL,
    ats_verified TINYINT NOT NULL,
    test_report_json LONGTEXT NULL,
    version_no INT NOT NULL,
    published_at DATETIME(3) NULL,
    retired_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_resume_layout_revision (template_id, revision_no)
);

CREATE TABLE resume_template_slots (
    id CHAR(36) PRIMARY KEY,
    template_version_id CHAR(36) NOT NULL,
    slot_key VARCHAR(64) NOT NULL,
    display_order INT NOT NULL,
    capacity_units INT NOT NULL,
    repeatable TINYINT NOT NULL,
    hide_when_empty TINYINT NOT NULL,
    overflow_strategy VARCHAR(32) NOT NULL,
    token_json TEXT NOT NULL,
    UNIQUE KEY uk_resume_template_slot (template_version_id, slot_key)
);

CREATE TABLE resume_layout_instances (
    id CHAR(36) PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    master_id CHAR(36) NOT NULL,
    content_version_id CHAR(36) NULL,
    template_version_id CHAR(36) NOT NULL,
    variant_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    overflow_json LONGTEXT NOT NULL,
    content_snapshot_json LONGTEXT NULL,
    template_snapshot_json LONGTEXT NULL,
    version_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    frozen_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL
);
CREATE INDEX idx_resume_layout_instances_owner ON resume_layout_instances(account_id, master_id, created_at);

CREATE TABLE resume_render_artifacts (
    id CHAR(36) PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    layout_instance_id CHAR(36) NOT NULL,
    format VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    renderer_version VARCHAR(32) NOT NULL,
    content_version_id CHAR(36) NOT NULL,
    template_version_id CHAR(36) NOT NULL,
    file_id CHAR(36) NULL,
    file_hash VARCHAR(64) NULL,
    validation_json LONGTEXT NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(1024) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_resume_render_artifacts_owner ON resume_render_artifacts(account_id, layout_instance_id, created_at);

INSERT INTO resume_layout_templates(id,display_name,family_name,language_code,recommended_pages,ats_candidate_level,photo_policy,variants_json,tags_json,status,created_at,updated_at) VALUES
('rlt-b-ats-minimal-v1','ATS 极简单栏','ATS 极简单栏','zh-CN','1','HIGH_UNVERIFIED','DISABLED','["MONO","BLUE"]','["ats","minimal"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-tech-single-v1','技术项目单页','技术单页','zh-CN','1','HIGH_UNVERIFIED','DISABLED','["MONO","BLUE"]','["technology","project"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-tech-double-v1','技术经历双页','技术双页','zh-CN','2','HIGH_UNVERIFIED','DISABLED','["BLUE","GRAY"]','["technology","experience"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-campus-v1','校园新锐','应届生','zh-CN','1','MEDIUM_UNVERIFIED','OPTIONAL','["NO_PHOTO","PHOTO"]','["campus"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-career-pro-v1','职场专业','成熟职场','zh-CN','1-2','HIGH_UNVERIFIED','OPTIONAL','["BLUE","MONO"]','["career"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-consulting-v1','咨询精英','管理咨询','zh-CN','1','HIGH_UNVERIFIED','DISABLED','["NO_PHOTO_01","NO_PHOTO_02"]','["consulting"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-finance-v1','金融财务','金融财务','zh-CN','1','MEDIUM_UNVERIFIED','OPTIONAL','["FINANCE_MINIMAL","BANKING_FORMAL"]','["finance"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-product-ops-v1','产品运营','产品运营','zh-CN','1','MEDIUM_UNVERIFIED','OPTIONAL','["MARKETING","ECOMMERCE"]','["product","operations"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-education-research-v1','教育研究','教育研究','zh-CN','1','MEDIUM_UNVERIFIED','OPTIONAL','["TEACHER","ACADEMIC"]','["education","research"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-english-single-v1','English One Page','英文单页','en','1','HIGH_UNVERIFIED','DISABLED','["CLASSIC","MODERN"]','["english"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-cn-table-v1','中文标准表格','传统中文表格','zh-CN','1','LOW_UNVERIFIED','OPTIONAL','["STANDARD","COMPACT"]','["table"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
('rlt-b-qa-data-v1','测试与数据','测试/数据分析','zh-CN','1','HIGH_UNVERIFIED','DISABLED','["QA","DATA"]','["qa","data"]','DRAFT',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));