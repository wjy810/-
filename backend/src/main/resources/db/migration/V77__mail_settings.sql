-- 后台邮件设置 (docs/phase2/05): the SMTP account an administrator configured. One row ('default');
-- when it is absent, mail goes out through the deployment's environment variables as before.
-- The password (an SMTP authorization code) is stored AES-GCM encrypted and never returned.
CREATE TABLE mail_settings (
    id VARCHAR(16) NOT NULL PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    security VARCHAR(16) NOT NULL,
    username VARCHAR(320) NOT NULL,
    password_ciphertext VARCHAR(1024) NOT NULL,
    from_address VARCHAR(320) NOT NULL,
    from_name VARCHAR(60) NOT NULL,
    notifications BOOLEAN NOT NULL,
    last_test_at DATETIME(3) NULL,
    last_test_ok BOOLEAN NULL,
    last_test_message VARCHAR(200) NULL,
    version_no INT NOT NULL DEFAULT 0,
    updated_by CHAR(36) NULL,
    updated_at DATETIME(3) NOT NULL
);
