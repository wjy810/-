-- CNY is an independent wallet; historical USD balances are never converted.
ALTER TABLE redeem_code_batches ADD COLUMN balance_amount DECIMAL(19,8) NULL;
CREATE TABLE commerce_prices (code VARCHAR(64) PRIMARY KEY, name VARCHAR(120) NOT NULL, amount DECIMAL(19,8) NULL, currency CHAR(3) NOT NULL DEFAULT 'CNY', days INT NOT NULL DEFAULT 0, enabled BOOLEAN NOT NULL DEFAULT FALSE, version INT NOT NULL DEFAULT 0);
INSERT INTO commerce_prices(code,name,amount,days,enabled) VALUES ('PRO_MONTH','PRO 月卡',19,30,TRUE),('PRO_QUARTER','PRO 季卡',49,90,TRUE),('PRO_YEAR','PRO 年卡',168,365,TRUE),('PERSONAL_CHANNEL','个人通道订阅',NULL,30,FALSE);
CREATE TABLE commerce_settings (id INT PRIMARY KEY, shop_enabled BOOLEAN NOT NULL, shop_url VARCHAR(2048) NOT NULL, shop_name VARCHAR(80) NOT NULL, shop_description VARCHAR(500) NOT NULL, version INT NOT NULL DEFAULT 0);
INSERT INTO commerce_settings VALUES (1,FALSE,'','链动小铺','前往外部小铺购买余额兑换码，付款后返回本站钱包兑换充值。',0);
CREATE TABLE commerce_orders (id CHAR(36) PRIMARY KEY, account_id CHAR(36) NOT NULL, request_id VARCHAR(100) NOT NULL, product_code VARCHAR(64) NOT NULL, price_version INT NOT NULL, amount DECIMAL(19,8) NOT NULL, currency CHAR(3) NOT NULL, days INT NOT NULL, expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL, CONSTRAINT uk_commerce_order_request UNIQUE(account_id,request_id));
CREATE INDEX idx_commerce_orders_account ON commerce_orders(account_id,created_at);
