-- 账号安全 (docs/phase2/11):
-- client_label: 登录时的设备与浏览器（例如“Windows · Chrome”），只记这么粗，用于登录设备列表和新设备登录提醒；
-- client_hash: 它的哈希，用来判断“这是不是一台没登录过的设备”。不记 IP，不记完整 User-Agent。
ALTER TABLE sessions ADD COLUMN client_label VARCHAR(80) NULL;
ALTER TABLE sessions ADD COLUMN client_hash CHAR(64) NULL;
