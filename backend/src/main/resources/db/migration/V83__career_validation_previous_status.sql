-- A validation remembers the node's status before it moved the node to 待验证, so a result that does not end in
-- mastery (rejected, 需要补强 or 材料不足) can put the node back instead of leaving it 待验证 for good.
ALTER TABLE career_ability_validations ADD COLUMN previous_node_status VARCHAR(32) NULL;
