package com.jobproof.modules.airesume.application;

import com.jobproof.modules.membership.application.MembershipService;
import com.jobproof.modules.aibilling.application.AiChargeService;
import com.jobproof.modules.aibilling.application.AiOperationCatalog;
import com.jobproof.modules.aibilling.application.BalanceWalletService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuotaService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final MembershipService memberships;
    private final BalanceWalletService wallets;
    private final AiChargeService charges;
    private final AuditService audit;

    public AiQuotaService(JdbcTemplate jdbc, ClockPort clock, MembershipService memberships,
            BalanceWalletService wallets, AiChargeService charges, AuditService audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.memberships = memberships;
        this.wallets = wallets;
        this.charges = charges;
        this.audit = audit;
    }

    /**
     * This month's allowance. The plan decides how many units a month grants (docs/phase2/02); an upgrade
     * during the month tops the current month up to the new plan, a lapsed plan never takes units back.
     */
    @Transactional
    public QuotaView current(String accountId) {
        wallets.lock(accountId);
        Instant now = clock.now();
        String period = PERIOD.format(now.atZone(ZONE));
        int grant = memberships.entitlements(accountId).aiMonthlyUnits();
        QuotaRow row = jdbc.query("SELECT * FROM ai_quota_accounts WHERE account_id=? AND period_key=? FOR UPDATE",
                this::row, accountId, period).stream().findFirst().orElse(null);
        if (row == null) {
            String id = Ids.newId();
            jdbc.update("INSERT INTO ai_quota_accounts(id,account_id,period_key,granted_units,used_units,held_units,version_no,created_at,updated_at) VALUES(?,?,?,?,0,0,0,?,?)",
                    id, accountId, period, grant, now, now);
            row = new QuotaRow(id, period, grant, 0, 0, 0);
            ledger(accountId, null, "GRANT", grant, grant, "monthly:" + period, now);
        } else if (row.grantedUnits() < grant) {
            int topUp = grant - row.grantedUnits();
            int updated = jdbc.update("UPDATE ai_quota_accounts SET granted_units=?,version_no=version_no+1,updated_at=? WHERE id=? AND granted_units<?",
                    grant, now, row.id(), grant);
            if (updated == 1) {
                row = new QuotaRow(row.id(), row.periodKey(), grant, row.usedUnits(), row.heldUnits(), row.versionNo() + 1);
                ledger(accountId, null, "GRANT", topUp, view(row).remainingUnits(), "plan-upgrade:" + period, now);
            }
        }
        return view(row);
    }

    /** Acquire before business rows so every settlement follows account-first lock order. */
    @Transactional
    public void lockForSettlement(String accountId) { wallets.lock(accountId); }

    /** Foreground requests cannot share a held reservation with another HTTP execution. */
    @Transactional
    public Reservation reserveExclusive(String accountId, String requestId, String operationCode, int units) {
        wallets.lock(accountId);
        var states = jdbc.queryForList("SELECT status FROM ai_quota_reservations WHERE account_id=? AND request_id=?", String.class, accountId, requestId);
        if (!states.isEmpty()) {
            if ("HELD".equals(states.getFirst())) throw AppException.conflict("AI_REQUEST_RUNNING", "该请求正在处理中，请稍后查看结果");
            throw AppException.conflict("AI_REQUEST_COMPLETED", "该请求已处理，请查看已有结果或发起新请求");
        }
        return reserve(accountId, requestId, operationCode, units);
    }

    @Transactional
    public Reservation reserve(String accountId, String requestId, String operationCode, int units) {
        if (units < 1 || units > 100) {
            throw AppException.user("AI_QUOTA_UNITS_INVALID", "AI 额度预占数量无效");
        }
        wallets.lock(accountId);
        var taskContext = AiExecutionContext.current();
        if (taskContext != null && taskContext.taskId() != null) {
            var states = jdbc.queryForList("SELECT status FROM async_tasks WHERE id=? AND account_id=? FOR UPDATE", String.class, taskContext.taskId(), accountId);
            if (states.isEmpty() || !java.util.Set.of("PENDING", "RUNNING").contains(states.getFirst()))
                throw AppException.conflict("AI_TASK_CANCELLED", "任务已结束，不再发起新的调用");
        }
        String normalized = AiOperationCatalog.normalize(operationCode);
        var existing = jdbc.query("SELECT * FROM ai_quota_reservations WHERE account_id=? AND request_id=? FOR UPDATE",
                this::reservationRow, accountId, requestId).stream().findFirst().orElse(null);
        if (existing != null) {
            if (!normalized.equals(AiOperationCatalog.normalize(existing.operation())) || units != existing.reservation().reservedUnits()) {
                throw AppException.conflict("AI_REQUEST_CHANGED", "相同请求编号的业务内容不一致");
            }
            if (!"HELD".equals(existing.reservation().status())) throw AppException.conflict("AI_REQUEST_COMPLETED", "该请求已处理，请查看已有结果或发起新请求");
            if (existing.personalChannelId() != null) charges.validatePersonal(accountId, existing.personalChannelId());
            AiExecutionContext.reservation(accountId, existing.reservation().id(), existing.personalChannelId());
            return existing.reservation();
        }
        var authorization = charges.authorize(accountId, normalized, units);
        QuotaView quota = "QUOTA".equals(authorization.source()) ? lockCurrent(accountId) : null;
        if (quota != null && quota.remainingUnits() < units) {
            throw AppException.conflict("AI_QUOTA_INSUFFICIENT",
                    "AI 次数不足：需要 " + units + " 次，当前剩余 " + quota.remainingUnits() + " 次");
        }
        Instant now = clock.now();
        String id = Ids.newId();
        String hold = "WALLET".equals(authorization.source()) ? wallets.hold(accountId, "ai:" + id,
                authorization.unitPrice().multiply(BigDecimal.valueOf(units))) : null;
        jdbc.update("INSERT INTO ai_quota_reservations(id,account_id,request_id,operation_code,reserved_units,settled_units,status,expires_at,created_at,updated_at,payment_source,quota_account_id,wallet_hold_id,unit_price,quote_id,personal_channel_id) VALUES(?,?,?,?,?,0,'HELD',?,?,?,?,?,?,?,?,?)",
                id, accountId, requestId, normalized, units, now.plusSeconds(604800), now, now,
                authorization.source(), quota == null ? null : quota.id(), hold, authorization.unitPrice(), authorization.quoteId(), authorization.personalChannelId());
        if (quota != null) {
            jdbc.update("UPDATE ai_quota_accounts SET held_units=held_units+?,version_no=version_no+1,updated_at=? WHERE id=?", units, now, quota.id());
            ledger(accountId, id, "RESERVE", -units, quota.remainingUnits() - units, requestId, now);
        }
        charges.bind(authorization, id);
        var execution = AiExecutionContext.current();
        if (execution != null && execution.taskId() != null) {
            if (jdbc.update("UPDATE async_tasks SET ai_reservation_id=?,ai_personal_channel_id=? WHERE id=? AND account_id=? AND status IN ('PENDING','RUNNING')",
                    id, authorization.personalChannelId(), execution.taskId(), accountId) != 1)
                throw AppException.conflict("AI_TASK_CANCELLED", "任务已结束，不再发起新的调用");
        }
        jdbc.update("INSERT INTO ai_usage_record(id,request_id,account_id,channel_id,model_id,status,requested_units,successful_units,started_at,payment_source,operation_code) VALUES(?,?,?,NULL,NULL,'RUNNING',?,0,?,?,?)",
                id, id, accountId, units, now, authorization.source(), normalized);
        AiExecutionContext.reservation(accountId, id, authorization.personalChannelId());
        return new Reservation(id, requestId, units, 0, "HELD");
    }

    @Transactional
    public void restoreReservation(String accountId, String reservationId) {
        wallets.lock(accountId);
        var taskContext = AiExecutionContext.current();
        if (taskContext != null && taskContext.taskId() != null) {
            var states = jdbc.queryForList("SELECT status FROM async_tasks WHERE id=? AND account_id=? FOR UPDATE", String.class, taskContext.taskId(), accountId);
            if (states.isEmpty() || !java.util.Set.of("PENDING", "RUNNING").contains(states.getFirst()))
                throw AppException.conflict("AI_TASK_CANCELLED", "任务已结束，不再继续调用");
        }
        var stored = requireReservation(accountId, reservationId);
        if (!"HELD".equals(stored.reservation().status()))
            throw AppException.conflict("AI_REQUEST_COMPLETED", "原分析预占已结束，请重新确认并发起分析");
        if (stored.personalChannelId() != null) charges.validatePersonal(accountId, stored.personalChannelId());
        var previous = AiExecutionContext.current();
        var quotes = jdbc.queryForList("SELECT q.id,q.request_path,q.payload_hash,q.payment_confirmed FROM ai_charge_quotes q WHERE q.reservation_id=? AND q.account_id=?",reservationId,accountId);
        var q = quotes.isEmpty() ? java.util.Map.<String,Object>of() : quotes.getFirst();
        AiExecutionContext.set(new AiExecutionContext.State((String)q.get("id"),(String)q.get("request_path"),(String)q.get("payload_hash"),Boolean.TRUE.equals(q.get("payment_confirmed")),accountId,reservationId,stored.personalChannelId(),previous==null?null:previous.taskId()));
    }

    @Transactional
    public QuotaView settle(String accountId, String reservationId, int successfulUnits) {
        wallets.lock(accountId);
        ReservationRow stored = requireReservation(accountId, reservationId);
        Reservation reservation = stored.reservation();
        if (!"HELD".equals(reservation.status())) {
            if (successfulUnits > reservation.settledUnits()) throw AppException.conflict("AI_REQUEST_COMPLETED", "任务预占已结束，请重新发起任务");
            return current(accountId);
        }
        if (successfulUnits < 0 || successfulUnits > reservation.reservedUnits()) throw AppException.user("AI_SETTLEMENT_INVALID", "结算次数超出预占数量");
        int saved = jdbc.queryForList("SELECT unit_key FROM ai_charge_successes WHERE reservation_id=? FOR UPDATE", String.class, reservationId).size();
        int settled = Math.max(successfulUnits, saved);
        Instant now = clock.now();
        if ("QUOTA".equals(stored.source())) {
            if (stored.quotaAccountId() == null) throw new IllegalStateException("Reservation has no original quota period");
            jdbc.update("UPDATE ai_quota_accounts SET held_units=held_units-?,used_units=used_units+?,version_no=version_no+1,updated_at=? WHERE id=?",
                    reservation.reservedUnits(), settled, now, stored.quotaAccountId());
            QuotaView original = view(jdbc.query("SELECT * FROM ai_quota_accounts WHERE id=?", this::row, stored.quotaAccountId()).getFirst());
            ledger(accountId, reservationId, "SETTLE", -settled, original.remainingUnits(), reservation.requestId(), now);
            if (settled < reservation.reservedUnits()) ledger(accountId, reservationId, "RELEASE", reservation.reservedUnits() - settled, original.remainingUnits(), reservation.requestId(), now);
        } else if ("WALLET".equals(stored.source())) {
            if (settled == 0) wallets.release(accountId, stored.holdId());
            else wallets.settle(accountId, stored.holdId(), stored.unitPrice().multiply(BigDecimal.valueOf(settled)));
        }
        jdbc.update("UPDATE ai_quota_reservations SET settled_units=?,status=?,updated_at=? WHERE id=?", settled, settled == 0 ? "RELEASED" : "SETTLED", now, reservationId);
        recordUsage(accountId, stored, settled, now);
        audit.append(accountId, settled == 0 ? "AI_CHARGE_RELEASED" : "AI_CHARGE_SETTLED", "AI_REQUEST", reservationId,
                "operation=" + stored.operation() + "; source=" + stored.source() + "; units=" + settled);
        QuotaView after = current(accountId);
        return after;
    }

    @Transactional
    public QuotaView release(String accountId, String reservationId) {
        return settle(accountId, reservationId, 0);
    }

    private ReservationRow requireReservation(String accountId, String id) {
        return jdbc.query("SELECT * FROM ai_quota_reservations WHERE id=? AND account_id=? FOR UPDATE", this::reservationRow, id, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_QUOTA_RESERVATION_NOT_FOUND", "AI 额度预占不存在"));
    }

    /** Save this marker in the same transaction as a batch result, so crash recovery can settle it. */
    @Transactional
    public void successfulUnit(String accountId, String reservationId, String unitKey) {
        wallets.lock(accountId);
        var row = requireReservation(accountId, reservationId);
        if (!"HELD".equals(row.reservation().status())) throw AppException.conflict("AI_REQUEST_COMPLETED", "任务已结算");
        var saved = jdbc.queryForList("SELECT unit_key FROM ai_charge_successes WHERE reservation_id=? FOR UPDATE", String.class, reservationId);
        if (!saved.contains(unitKey)) {
            int count = saved.size();
            if (count >= row.reservation().reservedUnits()) throw AppException.conflict("AI_SETTLEMENT_INVALID", "成功次数超出预占数量");
            jdbc.update("INSERT INTO ai_charge_successes(reservation_id,unit_key,created_at) VALUES(?,?,?)", reservationId, unitKey, clock.now());
        }
    }

    private ReservationRow reservationRow(ResultSet r, int index) throws SQLException {
        return new ReservationRow(new Reservation(r.getString("id"), r.getString("request_id"), r.getInt("reserved_units"), r.getInt("settled_units"), r.getString("status")),
                r.getString("operation_code"), r.getString("payment_source"), r.getString("quota_account_id"), r.getString("wallet_hold_id"), r.getBigDecimal("unit_price"), r.getString("personal_channel_id"));
    }

    private void recordUsage(String accountId, ReservationRow stored, int settled, Instant now) {
        String id = stored.reservation().id();
        var last = jdbc.queryForList("SELECT channel_id,model_code,input_tokens,output_tokens FROM ai_charge_attempts WHERE reservation_id=? ORDER BY created_at DESC FOR UPDATE", id);
        Object channel = last.isEmpty() ? null : last.getFirst().get("channel_id");
        Object model = last.isEmpty() ? null : last.getFirst().get("model_code");
        long input = last.stream().mapToLong(a -> ((Number) a.get("input_tokens")).longValue()).sum();
        long output = last.stream().mapToLong(a -> ((Number) a.get("output_tokens")).longValue()).sum();
        jdbc.update("UPDATE ai_usage_record SET status=?,successful_units=?,completed_at=?,channel_id=?,model_code=?,input_tokens=?,output_tokens=? WHERE id=?",
                settled == 0 ? "FAILED" : "SUCCEEDED", settled, now, channel, model, input, output, id);
        BigDecimal estimated = stored.unitPrice().multiply(BigDecimal.valueOf(stored.reservation().reservedUnits()));
        BigDecimal actual = stored.unitPrice().multiply(BigDecimal.valueOf(settled));
        jdbc.update("INSERT INTO ai_billing_record(id,usage_id,account_id,wallet_hold_id,status,currency,estimated_amount,actual_amount,refunded_amount,pricing_snapshot,channel_rate_snapshot,idempotency_key,version_no,settled_at,created_at,updated_at) VALUES(?,?,?,?,?,'CNY',?,?,?,?,1,?,0,?,?,?)",
                Ids.newId(), id, accountId, stored.holdId(), settled == 0 ? "RELEASED" : "SETTLED", estimated, actual, estimated.subtract(actual),
                "{\"operation\":\"" + stored.operation() + "\",\"source\":\"" + stored.source() + "\",\"unitPrice\":" + stored.unitPrice().toPlainString() + "}", "ai:" + id, now, now, now);
    }

    private record ReservationRow(Reservation reservation, String operation, String source, String quotaAccountId,
            String holdId, BigDecimal unitPrice, String personalChannelId) {}

    private QuotaView lockCurrent(String accountId) {
        current(accountId);
        String period = PERIOD.format(clock.now().atZone(ZONE));
        QuotaRow row = jdbc.query("SELECT * FROM ai_quota_accounts WHERE account_id=? AND period_key=? FOR UPDATE",
                this::row, accountId, period).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("AI quota account was not created"));
        return view(row);
    }

    private QuotaRow row(ResultSet rs, int rowNum) throws SQLException {
        return new QuotaRow(rs.getString("id"), rs.getString("period_key"), rs.getInt("granted_units"),
                rs.getInt("used_units"), rs.getInt("held_units"), rs.getInt("version_no"));
    }

    private static QuotaView view(QuotaRow row) {
        return new QuotaView(row.id(), row.periodKey(), row.grantedUnits(), row.usedUnits(), row.heldUnits(),
                Math.max(0, row.grantedUnits() - row.usedUnits() - row.heldUnits()), row.versionNo());
    }

    private void ledger(String accountId, String reservationId, String type, int units, int balance,
            String reference, Instant now) {
        jdbc.update("INSERT INTO ai_quota_ledger(id,account_id,reservation_id,ledger_type,units,balance_after,reference_id,created_at) VALUES(?,?,?,?,?,?,?,?)",
                Ids.newId(), accountId, reservationId, type, units, balance, reference, now);
    }

    private record QuotaRow(String id, String periodKey, int grantedUnits, int usedUnits, int heldUnits,
            int versionNo) {}
    public record QuotaView(String id, String periodKey, int grantedUnits, int usedUnits, int heldUnits,
            int remainingUnits, int versionNo) {}
    public record Reservation(String id, String requestId, int reservedUnits, int settledUnits, String status) {}
}
