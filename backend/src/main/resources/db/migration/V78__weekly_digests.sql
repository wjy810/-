-- 求职周报 (docs/phase2/06): which accounts were already summarised for which week, so a periodic sweep
-- sends each weekly digest once, on whichever instance claims it first.
CREATE TABLE weekly_digests (
    account_id CHAR(36) NOT NULL,
    week_key VARCHAR(10) NOT NULL,
    sent BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id, week_key)
);
