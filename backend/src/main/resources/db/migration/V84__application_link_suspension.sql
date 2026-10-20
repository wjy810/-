-- 投递追踪 (docs/phase2/01 第 7 节):
-- suspended_at: 投递归档时停用它的专属链接：公开页打不开、不占有效链接名额、不再提醒；恢复投递时一并恢复。
ALTER TABLE resume_share_links ADD COLUMN suspended_at DATETIME(3) NULL;

-- 已经归档的投递，链接现在就停用。
UPDATE resume_share_links SET suspended_at=CURRENT_TIMESTAMP(3)
 WHERE revoked_at IS NULL AND suspended_at IS NULL
   AND application_id IN (SELECT id FROM job_applications WHERE archived_at IS NOT NULL);

-- "想投"还没投出去：以前记录时预填的当天日期不是投递日期。
UPDATE job_applications SET applied_on=NULL WHERE stage='WISHLIST' AND furthest_stage='WISHLIST';
