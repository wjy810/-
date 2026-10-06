ALTER TABLE changelog_sections ADD COLUMN image_asset_id CHAR(36) NULL;
ALTER TABLE changelog_sections ADD COLUMN image_alt VARCHAR(255) NULL;
CREATE INDEX idx_changelog_sections_asset ON changelog_sections (image_asset_id);
