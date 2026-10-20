-- 投递追踪 (docs/phase2/01, 第二版):
-- archived_at: 删除改为归档，可以恢复，也可以从归档中彻底删除。
-- resume_owner_devices: 求职者本人用过的浏览器（打开投递追踪时登记），在这些浏览器上打开自己的链接不计回执。
-- resume_share_owner_visitors: 求职者标记"这是我自己打开的"读者，此后来自同一读者的访问也不计。
ALTER TABLE job_applications ADD COLUMN archived_at DATETIME(3) NULL;

CREATE TABLE resume_owner_devices (
    device_hash CHAR(64) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_resume_owner_devices_account ON resume_owner_devices(account_id);

CREATE TABLE resume_share_owner_visitors (
    link_id CHAR(36) NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (link_id, visitor_hash)
);
