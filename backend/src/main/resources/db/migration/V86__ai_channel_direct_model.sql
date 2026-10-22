-- A platform channel can use its tested upstream model without a catalog alias.
-- Empty provider_code is optional display metadata; personal channels retain their validation.
ALTER TABLE ai_channel ADD COLUMN selected_model VARCHAR(128) NULL;

UPDATE ai_channel SET selected_model = (
    SELECT tested_model FROM ai_channel_capability_test
    WHERE channel_id = ai_channel.id AND status = 'PASSED'
    ORDER BY checked_at DESC, id DESC LIMIT 1
) WHERE scope = 'SYSTEM';
