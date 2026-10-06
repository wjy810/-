package com.jobproof.modules.airesume.application;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuotaService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final int monthlyQuota;

    public AiQuotaService(JdbcTemplate jdbc, ClockPort clock,
            @Value("${jobproof.ai.workbench.monthly-quota:50}") int monthlyQuota) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.monthlyQuota = Math.max(0, monthlyQuota);
    }

    @Transactional
    public QuotaView current(String accountId) {
        Instant now = clock.now();
        String period = PERIOD.format(now.atZone(ZONE));
        QuotaRow row = jdbc.query("SELECT * FROM ai_quota_accounts WHERE account_id=? AND period_key=?",
                this::row, accountId, period).stream().findFirst().orElse(null);
        if (row == null) {
            String id = Ids.newId();
            jdbc.update("INSERT INTO ai_quota_accounts(id,account_id,period_key,granted_units,used_units,held_units,version_no,created_at,updated_at) VALUES(?,?,?,?,0,0,0,?,?)",
                    id, accountId, period, monthlyQuota, now, now);
            row = new QuotaRow(id, period, monthlyQuota, 0, 0, 0);
            ledger(accountId, null, "GRANT", monthlyQuota, monthlyQuota, "monthly:" + period, now);
        }
        return view(row);
    }

    @Transactional
    public Reservation reserve(String accountId, String requestId, String operationCode, int units) {
        if (units < 1 || units > 100) {
            throw AppException.user("AI_QUOTA_UNITS_INVALID", "AI 额度预占数量无效");
        }
        Reservation existing = jdbc.query("SELECT id,request_id,reserved_units,settled_units,status FROM ai_quota_reservations WHERE account_id=? AND request_id=?",
                (rs, n) -> new Reservation(rs.getString("id"), rs.getString("request_id"),
                        rs.getInt("reserved_units"), rs.getInt("settled_units"), rs.getString("status")),
                accountId, requestId).stream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        QuotaView quota = lockCurrent(accountId);
        if (quota.remainingUnits() < units) {
            throw AppException.conflict("AI_QUOTA_INSUFFICIENT",
                    "AI 次数不足：需要 " + units + " 次，当前剩余 " + quota.remainingUnits() + " 次");
        }
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO ai_quota_reservations(id,account_id,request_id,operation_code,reserved_units,settled_units,status,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,0,'HELD',?,?,?)",
                id, accountId, requestId, operationCode, units, now.plusSeconds(1800), now, now);
        jdbc.update("UPDATE ai_quota_accounts SET held_units=held_units+?,version_no=version_no+1,updated_at=? WHERE id=?",
                units, now, quota.id());
        QuotaView after = current(accountId);
        ledger(accountId, id, "RESERVE", -units, after.remainingUnits(), requestId, now);
        return new Reservation(id, requestId, units, 0, "HELD");
    }

    @Transactional
    public QuotaView settle(String accountId, String reservationId, int successfulUnits) {
        Reservation reservation = requireReservation(accountId, reservationId);
        if (!"HELD".equals(reservation.status())) {
            return current(accountId);
        }
        int settled = Math.max(0, Math.min(successfulUnits, reservation.reservedUnits()));
        Instant now = clock.now();
        QuotaView quota = current(accountId);
        jdbc.update("UPDATE ai_quota_accounts SET held_units=held_units-?,used_units=used_units+?,version_no=version_no+1,updated_at=? WHERE id=?",
                reservation.reservedUnits(), settled, now, quota.id());
        jdbc.update("UPDATE ai_quota_reservations SET settled_units=?,status='SETTLED',updated_at=? WHERE id=?",
                settled, now, reservationId);
        QuotaView after = current(accountId);
        ledger(accountId, reservationId, "SETTLE", -settled, after.remainingUnits(), reservation.requestId(), now);
        int refunded = reservation.reservedUnits() - settled;
        if (refunded > 0) {
            ledger(accountId, reservationId, "RELEASE", refunded, after.remainingUnits(), reservation.requestId(), now);
        }
        return after;
    }

    @Transactional
    public QuotaView release(String accountId, String reservationId) {
        return settle(accountId, reservationId, 0);
    }

    private Reservation requireReservation(String accountId, String id) {
        return jdbc.query("SELECT id,request_id,reserved_units,settled_units,status FROM ai_quota_reservations WHERE id=? AND account_id=? FOR UPDATE",
                (rs, n) -> new Reservation(rs.getString("id"), rs.getString("request_id"),
                        rs.getInt("reserved_units"), rs.getInt("settled_units"), rs.getString("status")),
                id, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_QUOTA_RESERVATION_NOT_FOUND", "AI 额度预占不存在"));
    }

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
