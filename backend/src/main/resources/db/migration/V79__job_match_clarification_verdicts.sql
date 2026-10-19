-- 岗位匹配澄清 (docs/phase2/07): the verdict each option implies, as the model wrote it, and the verdict of
-- the answer the user picked. Scoring reads answer_verdict instead of guessing from the option's wording.
ALTER TABLE job_match_clarifications ADD COLUMN option_verdicts_json TEXT NULL;
ALTER TABLE job_match_clarifications ADD COLUMN answer_verdict VARCHAR(16) NULL;
