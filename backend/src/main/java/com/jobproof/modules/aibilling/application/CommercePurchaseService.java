package com.jobproof.modules.aibilling.application;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Service
public class CommercePurchaseService {
    private final JdbcTemplate jdbc;
    private final BalanceWalletService wallet;
    private final ClockPort clock;
    private final com.jobproof.modules.audit.application.AuditService audit;

    public CommercePurchaseService(
            JdbcTemplate jdbc, BalanceWalletService wallet, ClockPort clock, com.jobproof.modules.audit.application.AuditService audit) {
        this.jdbc = jdbc;
        this.wallet = wallet;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public Order purchase(String accountId, Purchase command) {
        if (command == null
                || command.requestId() == null
                || command.requestId().isBlank()
                || command.requestId().length() > 100)
            throw AppException.user("IDEMPOTENCY_KEY_REQUIRED", "需要有效请求标识");
        if (command.code() == null
                || !java.util.Set.of("PRO_MONTH", "PRO_QUARTER", "PRO_YEAR", "PERSONAL_CHANNEL")
                        .contains(command.code()))
            throw AppException.user("COMMERCE_PRODUCT_INVALID", "商品不存在");
        wallet.lock(accountId);
        if (!java.util.Set.of("USER", "SEEKER", "ADMIN")
                .contains(
                        jdbc.queryForObject(
                                "SELECT role FROM accounts WHERE id=?", String.class, accountId)))
            throw AppException.forbidden("COMMERCE_FORBIDDEN", "仅求职者可购买订阅");
        var old =
                jdbc.query(
                        "SELECT * FROM commerce_orders WHERE account_id=? AND request_id=?",
                        (r, n) ->
                                new Order(
                                        r.getString("id"),
                                        r.getString("product_code"),
                                        r.getInt("price_version"),
                                        r.getBigDecimal("amount"),
                                        r.getTimestamp("expires_at").toInstant()),
                        accountId,
                        command.requestId());
        if (!old.isEmpty()) {
            var o = old.getFirst();
            if (!o.code().equals(command.code()) || o.priceVersion() != command.priceVersion())
                throw AppException.conflict("IDEMPOTENCY_CONFLICT", "重复请求内容不同");
            return o;
        }
        // A locking read is a current read in MySQL REPEATABLE READ. Map and validate
        // that same row: a later ordinary SELECT could return an older transaction snapshot.
        var lockedPrices =
                jdbc.query(
                        "SELECT code,name,amount,currency,days,enabled,version FROM commerce_prices"
                                + " WHERE code=? FOR UPDATE",
                        (r, n) ->
                                new CommerceCatalogService.ProductPrice(
                                        r.getString("code"),
                                        r.getString("name"),
                                        r.getBigDecimal("amount"),
                                        r.getString("currency"),
                                        r.getInt("days"),
                                        r.getBoolean("enabled"),
                                        r.getInt("version")),
                        command.code());
        if (lockedPrices.isEmpty())
            throw AppException.conflict("COMMERCE_PRICE_UNAVAILABLE", "价格尚未配置或商品已停用");
        var price = lockedPrices.getFirst();
        if (!price.enabled()
                || price.amount() == null
                || price.amount().signum() < 0
                || !"CNY".equals(price.currency()))
            throw AppException.conflict("COMMERCE_PRICE_UNAVAILABLE", "价格尚未配置或商品已停用");
        if (price.days() < 1
                || price.days() > 3650
                || ("PERSONAL_CHANNEL".equals(price.code()) && price.days() != 30))
            throw AppException.conflict("COMMERCE_PRICE_UNAVAILABLE", "商品有效期配置不合法");
        if (price.version() != command.priceVersion())
            throw AppException.conflict("COMMERCE_PRICE_CHANGED", "价格已更新，请确认新价格后重试");
        Instant now = clock.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String id = Ids.newId();
        wallet.debit(accountId, "ORDER:" + id, price.amount());
        Instant expires;
        if ("PERSONAL_CHANNEL".equals(price.code())) {
            var rows =
                    jdbc.query(
                            "SELECT current_period_end FROM personal_channel_subscription WHERE"
                                    + " account_id=? FOR UPDATE",
                            (r, n) -> r.getTimestamp(1).toInstant(),
                            accountId);
            Instant base = rows.isEmpty() || !rows.getFirst().isAfter(now) ? now : rows.getFirst();
            expires = base.plus(Duration.ofDays(30));
            if (rows.isEmpty())
                jdbc.update(
                        "INSERT INTO"
                            + " personal_channel_subscription(id,account_id,status,price,currency,current_period_start,current_period_end,auto_renew,idempotency_key,version_no,created_at,updated_at)"
                            + " VALUES(?,?,'ACTIVE',?,'CNY',?,?,FALSE,?,0,?,?)",
                        Ids.newId(),
                        accountId,
                        price.amount(),
                        now,
                        expires,
                        id,
                        now,
                        now);
            else
                jdbc.update(
                        "UPDATE personal_channel_subscription SET"
                            + " status='ACTIVE',price=?,currency='CNY',current_period_start=?,current_period_end=?,auto_renew=FALSE,idempotency_key=?,version_no=version_no+1,updated_at=?"
                            + " WHERE account_id=?",
                        price.amount(),
                        now,
                        expires,
                        id,
                        now,
                        accountId);
        } else {
            var rows =
                    jdbc.query(
                            "SELECT expires_at FROM account_memberships WHERE account_id=? FOR"
                                    + " UPDATE",
                            (r, n) ->
                                    r.getTimestamp(1) == null
                                            ? null
                                            : r.getTimestamp(1).toInstant(),
                            accountId);
            Instant base =
                    rows.isEmpty() || rows.getFirst() == null || !rows.getFirst().isAfter(now)
                            ? now
                            : rows.getFirst();
            expires = base.plus(Duration.ofDays(price.days()));
            if (rows.isEmpty())
                jdbc.update(
                        "INSERT INTO"
                            + " account_memberships(account_id,plan,expires_at,source,version_no,created_at,updated_at)"
                            + " VALUES(?,'PRO',?,'WALLET',0,?,?)",
                        accountId,
                        expires,
                        now,
                        now);
            else
                jdbc.update(
                        "UPDATE account_memberships SET"
                            + " plan='PRO',expires_at=?,source='WALLET',version_no=version_no+1,updated_at=?"
                            + " WHERE account_id=?",
                        expires,
                        now,
                        accountId);
            jdbc.update(
                    "INSERT INTO"
                        + " membership_events(id,account_id,event_type,plan,days,expires_at,detail,created_at)"
                        + " VALUES(?,?,'PURCHASED','PRO',?,?,?,?)",
                    Ids.newId(),
                    accountId,
                    price.days(),
                    expires,
                    price.name(),
                    now);
        }
        jdbc.update(
                "INSERT INTO"
                    + " commerce_orders(id,account_id,request_id,product_code,price_version,amount,currency,days,expires_at,created_at)"
                    + " VALUES(?,?,?,?,?,?,'CNY',?,?,?)",
                id,
                accountId,
                command.requestId(),
                price.code(),
                price.version(),
                price.amount(),
                price.days(),
                expires,
                now);
        audit.append(accountId, "COMMERCE_PURCHASED", "COMMERCE_ORDER", id,
                "product=" + price.code() + ", priceVersion=" + price.version() + ", currency=CNY, amount=" + price.amount() + ", expiresAt=" + expires);
        return new Order(id, price.code(), price.version(), price.amount(), expires);
    }

    public record Purchase(String code, int priceVersion, String requestId) {}

    public record Order(
            String id, String code, int priceVersion, BigDecimal amount, Instant expiresAt) {}
}
