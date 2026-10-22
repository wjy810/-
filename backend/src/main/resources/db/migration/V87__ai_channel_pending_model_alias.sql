-- Legacy aliases are staged with capability results and applied only on explicit activation.
ALTER TABLE ai_channel_capability_test ADD COLUMN platform_model_code VARCHAR(128) NULL;
