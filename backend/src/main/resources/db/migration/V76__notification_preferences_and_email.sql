-- Notification preferences and email delivery (docs/phase2/03-通知偏好与邮件提醒.md).
-- Per account and type: whether to show it in the notification center and whether to email it.
CREATE TABLE notification_preferences (
    account_id CHAR(36) NOT NULL,
    type VARCHAR(48) NOT NULL,
    in_app BOOLEAN NOT NULL,
    email BOOLEAN NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id, type)
);

-- Outbox of notification emails. One row per notification event; a sweep sends due rows with retries.
CREATE TABLE notification_emails (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    type VARCHAR(48) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    to_address VARCHAR(320) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body VARCHAR(1024) NOT NULL,
    action_path VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    last_error VARCHAR(200) NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_notification_email_event UNIQUE (account_id, type, event_id)
);
CREATE INDEX idx_notification_emails_due ON notification_emails(status, next_attempt_at);
