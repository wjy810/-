-- 重置验证码错误次数：超过上限后作废，避免 6 位码在线穷举。
ALTER TABLE password_reset_challenges
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
