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

/**
 * Persistent CNY wallet. The existing account row serializes creation and every balance mutation.
 */
@Service
public class BalanceWalletService {
    private final JdbcTemplate jdbc;
    private final ClockPort clock;

    public BalanceWalletService(JdbcTemplate jdbc, ClockPort clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void lock(String accountId) {
        if (jdbc.queryForList(
                        "SELECT id FROM accounts WHERE id=? FOR UPDATE", String.class, accountId)
                .isEmpty()) throw AppException.user("ACCOUNT_NOT_FOUND", "账号不存在");
    }

    @Transactional
    public WalletView wallet(String accountId) {
        lock(accountId);
        return ensure(accountId);
    }

    // Callers may already have a REPEATABLE READ snapshot. Every balance/idempotency
    // lookup must be a current read even after acquiring the account serialization lock.
    private WalletView ensure(String id) {
        var rows =
                jdbc.query(
                        "SELECT * FROM wallet_account WHERE account_id=? AND currency='CNY' FOR"
                            + " UPDATE",
                        (r, n) ->
                                new WalletView(
                                        r.getString("id"),
                                        id,
                                        "CNY",
                                        r.getBigDecimal("available_balance"),
                                        r.getBigDecimal("held_balance"),
                                        r.getInt("version_no"),
                                        r.getTimestamp("updated_at").toInstant()),
                        id);
        if (!rows.isEmpty()) return rows.getFirst();
        Instant now = clock.now();
        String wid = Ids.newId();
        jdbc.update(
                "INSERT INTO"
                    + " wallet_account(id,account_id,currency,available_balance,held_balance,version_no,created_at,updated_at)"
                    + " VALUES(?,?,'CNY',0,0,0,?,?)",
                wid,
                id,
                now,
                now);
        return new WalletView(wid, id, "CNY", BigDecimal.ZERO, BigDecimal.ZERO, 0, now);
    }

    public static void amount(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.scale() > 8
                || amount.precision() - amount.scale() > 11)
            throw AppException.user("WALLET_AMOUNT_INVALID", "金额不合法");
    }

    private void key(String key) {
        if (key == null || key.isBlank() || key.length() > 100)
            throw AppException.user("IDEMPOTENCY_KEY_REQUIRED", "需要有效请求标识");
    }

    @Transactional
    public void credit(String accountId, String key, BigDecimal amount) {
        change(accountId, key, amount, "CREDIT", false);
    }

    @Transactional
    public void debit(String accountId, String key, BigDecimal amount) {
        change(accountId, key, amount, "DEBIT", true);
    }

    private void change(String id, String key, BigDecimal amount, String type, boolean debit) {
        amount(amount);
        key(key);
        lock(id);
        var w = ensure(id);
        var prior =
                jdbc.queryForList(
                        "SELECT amount FROM wallet_ledger WHERE account_id=? AND ledger_type=? AND"
                                + " idempotency_key=? FOR UPDATE",
                        BigDecimal.class,
                        id,
                        type,
                        "CNY:" + key);
        if (!prior.isEmpty()) {
            if (prior.getFirst().compareTo(amount) != 0)
                throw AppException.conflict("IDEMPOTENCY_CONFLICT", "重复请求的金额不一致");
            return;
        }
        if (debit && w.availableBalance().compareTo(amount) < 0)
            throw AppException.conflict("WALLET_INSUFFICIENT", "余额不足，请先充值");
        BigDecimal next =
                debit ? w.availableBalance().subtract(amount) : w.availableBalance().add(amount);
        update(w, next, w.heldBalance());
        ledger(w, type, amount, next, key, null);
    }

    @Transactional
    public String hold(String id, String requestId, BigDecimal amount) {
        amount(amount);
        key(requestId);
        lock(id);
        var w = ensure(id);
        var prior =
                jdbc.query(
                        "SELECT id,amount,status FROM wallet_hold WHERE account_id=? AND"
                                + " request_id=? FOR UPDATE",
                        (r, n) ->
                                new Hold(
                                        r.getString(1),
                                        r.getBigDecimal(2),
                                        BigDecimal.ZERO,
                                        r.getString(3)),
                        id,
                        "CNY:" + requestId);
        if (!prior.isEmpty()) {
            var h = prior.getFirst();
            if (h.amount().compareTo(amount) != 0)
                throw AppException.conflict("IDEMPOTENCY_CONFLICT", "重复请求的金额不一致");
            return h.id();
        }
        if (w.availableBalance().compareTo(amount) < 0)
            throw AppException.conflict("WALLET_INSUFFICIENT", "余额不足，请先充值");
        String hid = Ids.newId();
        Instant now = clock.now();
        jdbc.update(
                "INSERT INTO"
                    + " wallet_hold(id,wallet_account_id,account_id,request_id,amount,settled_amount,status,idempotency_key,expires_at,version_no,created_at,updated_at)"
                    + " VALUES(?,?,?,?,?,0,'HELD',?,?,0,?,?)",
                hid,
                w.id(),
                id,
                "CNY:" + requestId,
                amount,
                "CNY:" + requestId,
                now.plus(Duration.ofDays(7)),
                now,
                now);
        update(w, w.availableBalance().subtract(amount), w.heldBalance().add(amount));
        ledger(w, "HOLD", amount, w.availableBalance().subtract(amount), hid, hid);
        return hid;
    }

    @Transactional
    public void settle(String id, String holdId, BigDecimal amount) {
        finish(id, holdId, amount, false);
    }

    @Transactional
    public void release(String id, String holdId) {
        finish(id, holdId, BigDecimal.ZERO, true);
    }

    private void finish(String id, String hid, BigDecimal value, boolean release) {
        amount(value);
        lock(id);
        var w = ensure(id);
        var rows =
                jdbc.query(
                        "SELECT id,amount,settled_amount,status FROM wallet_hold WHERE id=? AND"
                                + " account_id=? AND wallet_account_id=? FOR UPDATE",
                        (r, n) ->
                                new Hold(
                                        r.getString(1),
                                        r.getBigDecimal(2),
                                        r.getBigDecimal(3),
                                        r.getString(4)),
                        hid,
                        id,
                        w.id());
        if (rows.isEmpty()) throw AppException.user("WALLET_HOLD_NOT_FOUND", "冻结记录不存在");
        var h = rows.getFirst();
        if (!"HELD".equals(h.status())) {
            if (release && "RELEASED".equals(h.status())
                    || !release
                            && "SETTLED".equals(h.status())
                            && h.settled().compareTo(value) == 0) return;
            throw AppException.conflict("WALLET_HOLD_CLOSED", "冻结记录已处理");
        }
        if (value.compareTo(h.amount()) > 0)
            throw AppException.conflict("WALLET_OVER_SETTLEMENT", "结算超过冻结金额");
        BigDecimal next = w.availableBalance().add(h.amount()).subtract(value);
        update(w, next, w.heldBalance().subtract(h.amount()));
        jdbc.update(
                "UPDATE wallet_hold SET"
                    + " status=?,settled_amount=?,version_no=version_no+1,updated_at=? WHERE id=?",
                release ? "RELEASED" : "SETTLED",
                value,
                clock.now(),
                hid);
        ledger(w, release ? "RELEASE" : "SETTLE", release ? h.amount() : value, next, hid, hid);
    }

    private void update(WalletView w, BigDecimal available, BigDecimal held) {
        jdbc.update(
                "UPDATE wallet_account SET"
                        + " available_balance=?,held_balance=?,version_no=version_no+1,updated_at=?"
                        + " WHERE id=?",
                available,
                held,
                clock.now(),
                w.id());
    }

    private void ledger(
            WalletView w, String type, BigDecimal amount, BigDecimal next, String key, String ref) {
        jdbc.update(
                "INSERT INTO"
                    + " wallet_ledger(id,wallet_account_id,account_id,ledger_type,amount,balance_after,reference_type,reference_id,idempotency_key,description,created_at)"
                    + " VALUES(?,?,?,?,?,?,'COMMERCE',?,?,?,?)",
                Ids.newId(),
                w.id(),
                w.accountId(),
                type,
                amount,
                next,
                ref,
                "CNY:" + key,
                type,
                clock.now());
    }

    private record Hold(String id, BigDecimal amount, BigDecimal settled, String status) {}

    public record WalletView(
            String id,
            String accountId,
            String currency,
            BigDecimal availableBalance,
            BigDecimal heldBalance,
            int versionNo,
            Instant updatedAt) {}
}
