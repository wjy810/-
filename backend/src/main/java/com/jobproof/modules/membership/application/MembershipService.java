package com.jobproof.modules.membership.application;

import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.membership.domain.MembershipPlan;
import com.jobproof.modules.membership.domain.RedeemCodes;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.time.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员与兑换码 (docs/phase2/02). The plan decides the monthly AI allowance and how many resume links may
 * be active; PRO is held until an expiry that each redeemed code extends. There is no payment channel
 * yet: codes are issued in batches (schools, training providers, promotions) and redeemed by users.
 */
@Service
public class MembershipService implements DeletionModuleHandler, AccountExportContributor {

    static final Set<Integer> DURATIONS = Set.of(7, 30, 90, 180, 365);
    static final int MAX_BATCH = 500;
    static final int FAILED_ATTEMPTS_PER_HOUR = 10;
    /** Redeemed codes outlive a deleted account, pointing at nobody. */
    static final String DELETED_ACCOUNT = "00000000-0000-0000-0000-000000000000";

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ClockPort clock;
    private final Entitlements free;
    private final Entitlements pro;

    public MembershipService(JdbcTemplate jdbc, AuditService audit, ClockPort clock,
            @Value("${jobproof.ai.workbench.monthly-quota:50}") int freeAiUnits,
            @Value("${jobproof.membership.free.active-links:3}") int freeLinks,
            @Value("${jobproof.membership.pro.ai-monthly-units:300}") int proAiUnits,
            @Value("${jobproof.membership.pro.active-links:20}") int proLinks) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.free = new Entitlements(MembershipPlan.FREE.name(), Math.max(0, freeAiUnits), Math.max(1, freeLinks));
        // A paid plan never gives less than the free one, whatever the deployment configures.
        this.pro = new Entitlements(MembershipPlan.PRO.name(), Math.max(this.free.aiMonthlyUnits(), proAiUnits),
                Math.max(this.free.activeLinks(), proLinks));
    }

    @Override public String moduleCode() { return "membership"; }
    @Override public String moduleKey() { return "membership"; }

    /** What the account may use right now; an expired PRO is FREE again. */
    @Transactional(readOnly = true)
    public Entitlements entitlements(String accountId) {
        return effectivePlan(accountId, clock.now()) == MembershipPlan.PRO ? pro : free;
    }

    @Transactional(readOnly = true)
    public PlanCatalog catalog() {
        return new PlanCatalog(free, pro, List.of(
                new Price("MONTH", "月付", 1900, 30),
                new Price("QUARTER", "季付", 4900, 90),
                new Price("YEAR", "年付", 16800, 365)));
    }

    @Transactional(readOnly = true)
    public MembershipView current(CurrentAccount current) {
        assertUser(current);
        return view(current.accountId());
    }

    /**
     * Redeems a code for the caller. Each code works once; a code's days are added to a PRO that is
     * still running, so stacking codes never wastes time already paid for.
     */
    @Transactional(noRollbackFor = AppException.class)
    public MembershipView redeem(CurrentAccount current, String input) {
        assertUser(current);
        String accountId = current.accountId();
        Instant now = clock.now();
        Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM redeem_attempts WHERE account_id=? AND succeeded=FALSE AND created_at>?",
                Integer.class, accountId, now.minus(Duration.ofHours(1)));
        if (failed != null && failed >= FAILED_ATTEMPTS_PER_HOUR) {
            throw AppException.rateLimited("REDEEM_TOO_MANY_ATTEMPTS", "尝试次数过多，请一小时后再试");
        }
        String canonical = RedeemCodes.normalize(input).orElse(null);
        if (canonical == null) throw failure(accountId, now, "REDEEM_CODE_INVALID", "兑换码格式不正确，应为 JP- 开头的 16 位字母数字");
        List<CodeRow> rows = jdbc.query("""
                SELECT c.id,c.batch_id,c.redeemed_by,b.plan,b.duration_days,b.redeem_before,b.revoked_at,b.name
                  FROM redeem_codes c JOIN redeem_code_batches b ON b.id=c.batch_id WHERE c.code_hash=?
                """, (rs, n) -> new CodeRow(rs.getString("id"), rs.getString("batch_id"), rs.getString("redeemed_by"),
                        rs.getString("plan"), rs.getInt("duration_days"), instant(rs, "redeem_before"),
                        instant(rs, "revoked_at"), rs.getString("name")),
                Tokens.sha256(canonical));
        if (rows.isEmpty()) throw failure(accountId, now, "REDEEM_CODE_INVALID", "兑换码无效，请核对后重试");
        CodeRow code = rows.get(0);
        if (code.redeemedBy() != null) {
            throw accountId.equals(code.redeemedBy())
                    ? AppException.conflict("REDEEM_CODE_ALREADY_YOURS", "你已经兑换过这个码")
                    : failure(accountId, now, "REDEEM_CODE_USED", "这个兑换码已被使用");
        }
        if (code.revokedAt() != null) throw failure(accountId, now, "REDEEM_CODE_REVOKED", "这个兑换码已停用");
        if (code.redeemBefore() != null && !code.redeemBefore().isAfter(now)) {
            throw failure(accountId, now, "REDEEM_CODE_EXPIRED", "这个兑换码已过兑换期限");
        }
        int claimed = jdbc.update("UPDATE redeem_codes SET redeemed_by=?,redeemed_at=? WHERE id=? AND redeemed_by IS NULL",
                accountId, now, code.id());
        if (claimed == 0) throw failure(accountId, now, "REDEEM_CODE_USED", "这个兑换码已被使用");
        jdbc.update("UPDATE redeem_code_batches SET redeemed_count=redeemed_count+1 WHERE id=?", code.batchId());
        Instant expires = extend(accountId, code.durationDays(), "REDEEM_CODE", now);
        jdbc.update("INSERT INTO membership_events(id,account_id,event_type,plan,days,batch_id,expires_at,detail,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                Ids.newId(), accountId, "REDEEMED", code.plan(), code.durationDays(), code.batchId(), expires,
                clip(code.batchName(), 200), now);
        jdbc.update("INSERT INTO redeem_attempts(id,account_id,succeeded,created_at) VALUES(?,?,TRUE,?)", Ids.newId(), accountId, now);
        audit.append(accountId, "MEMBERSHIP_REDEEMED", "REDEEM_CODE", code.id(), "兑换 " + code.durationDays() + " 天专业版");
        return view(accountId);
    }

    // ---- operations: batches of codes ---------------------------------------------------------------

    /** Creates a batch and returns its codes in plain text, this once; only their hashes are kept. */
    @Transactional
    public BatchCreated createBatch(CurrentAccount current, BatchCommand command) {
        assertAdmin(current);
        if (command == null) throw AppException.user("REDEEM_BATCH_INVALID", "请填写兑换码批次信息");
        String name = command.name() == null ? "" : command.name().trim();
        if (name.isEmpty() || name.length() > 120) throw AppException.user("REDEEM_BATCH_NAME", "批次名称需要 1 到 120 个字");
        if (command.durationDays() == null || !DURATIONS.contains(command.durationDays())) {
            throw AppException.user("REDEEM_BATCH_DURATION", "时长只能是 7、30、90、180 或 365 天");
        }
        int quantity = command.quantity() == null ? 0 : command.quantity();
        if (quantity < 1 || quantity > MAX_BATCH) throw AppException.user("REDEEM_BATCH_QUANTITY", "每批数量为 1 到 " + MAX_BATCH + " 个");
        Instant now = clock.now();
        if (command.redeemBefore() != null && !command.redeemBefore().isAfter(now)) {
            throw AppException.user("REDEEM_BATCH_DEADLINE", "兑换截止时间需要晚于现在");
        }
        String batchId = Ids.newId();
        String channel = command.channel() == null || command.channel().isBlank() ? null : clip(command.channel().trim(), 120);
        jdbc.update("INSERT INTO redeem_code_batches(id,name,channel,plan,duration_days,quantity,redeemed_count,redeem_before,revoked_at,created_by,created_at) VALUES(?,?,?,?,?,?,0,?,NULL,?,?)",
                batchId, name, channel, MembershipPlan.PRO.name(), command.durationDays(), quantity, command.redeemBefore(),
                current.accountId(), now);
        List<String> codes = new ArrayList<>(quantity);
        while (codes.size() < quantity) {
            String display = RedeemCodes.generate();
            String canonical = RedeemCodes.normalize(display).orElseThrow();
            try {
                jdbc.update("INSERT INTO redeem_codes(id,batch_id,code_hash,code_hint,redeemed_by,redeemed_at,created_at) VALUES(?,?,?,?,NULL,NULL,?)",
                        Ids.newId(), batchId, Tokens.sha256(canonical), RedeemCodes.hint(canonical), now);
                codes.add(display);
            } catch (DuplicateKeyException collision) {
                // 80 random bits make this practically impossible; draw again if it ever happens.
            }
        }
        audit.append(current.accountId(), "REDEEM_BATCH_CREATED", "REDEEM_BATCH", batchId,
                "生成兑换码 " + quantity + " 个（" + command.durationDays() + " 天）");
        return new BatchCreated(batch(batchId, now), codes);
    }

    @Transactional(readOnly = true)
    public List<BatchView> batches(CurrentAccount current) {
        assertAdmin(current);
        Instant now = clock.now();
        return jdbc.query("SELECT * FROM redeem_code_batches ORDER BY created_at DESC LIMIT 200", (rs, n) -> batchView(rs, now));
    }

    @Transactional
    public BatchView revokeBatch(CurrentAccount current, String batchId) {
        assertAdmin(current);
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE redeem_code_batches SET revoked_at=? WHERE id=? AND revoked_at IS NULL", now, batchId);
        if (changed > 0) audit.append(current.accountId(), "REDEEM_BATCH_REVOKED", "REDEEM_BATCH", batchId, "停用兑换码批次");
        return batch(batchId, now);
    }

    // ---- data rights --------------------------------------------------------------------------------

    @Override
    public Result onAccountDeletion(String accountId) {
        try {
            jdbc.update("UPDATE redeem_codes SET redeemed_by=? WHERE redeemed_by=?", DELETED_ACCOUNT, accountId);
            jdbc.update("DELETE FROM redeem_attempts WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM membership_events WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM account_memberships WHERE account_id=?", accountId);
            return Result.succeeded("会员记录已删除，已兑换的码不再关联到你");
        } catch (RuntimeException e) {
            return Result.failed("会员记录暂未清除，删除申请会保持可重试");
        }
    }

    @Override
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("membership", jdbc.queryForList("SELECT plan,expires_at,source,created_at,updated_at FROM account_memberships WHERE account_id=?", accountId));
        result.put("events", jdbc.queryForList("SELECT event_type,plan,days,expires_at,detail,created_at FROM membership_events WHERE account_id=? ORDER BY created_at", accountId));
        return result;
    }

    // ---- internals --------------------------------------------------------------------------------

    private MembershipView view(String accountId) {
        Instant now = clock.now();
        Optional<Instant> expires = proExpiry(accountId);
        boolean active = expires.isPresent() && expires.get().isAfter(now);
        Integer links = jdbc.queryForObject("SELECT COUNT(*) FROM resume_share_links WHERE account_id=? AND revoked_at IS NULL AND suspended_at IS NULL AND (expires_at IS NULL OR expires_at>?)",
                Integer.class, accountId, now);
        List<EventView> events = jdbc.query("SELECT * FROM membership_events WHERE account_id=? ORDER BY created_at DESC LIMIT 20",
                (rs, n) -> new EventView(rs.getString("id"), rs.getString("event_type"), rs.getString("plan"),
                        rs.getInt("days"), instant(rs, "expires_at"), rs.getString("detail"), instant(rs, "created_at")),
                accountId);
        return new MembershipView(active ? MembershipPlan.PRO.name() : MembershipPlan.FREE.name(),
                active ? expires.get() : null, expires.isPresent() && !active ? expires.get() : null,
                active ? pro : free, free, pro, links == null ? 0 : links, events);
    }

    private MembershipPlan effectivePlan(String accountId, Instant now) {
        return proExpiry(accountId).filter(expires -> expires.isAfter(now))
                .map(expires -> MembershipPlan.PRO).orElse(MembershipPlan.FREE);
    }

    /** When the account's PRO ends (or ended); empty for an account that never had PRO. */
    private Optional<Instant> proExpiry(String accountId) {
        List<Timestamp> rows = jdbc.queryForList("SELECT expires_at FROM account_memberships WHERE account_id=? AND plan='PRO'",
                Timestamp.class, accountId);
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0).toInstant());
    }

    private Instant extend(String accountId, int days, String source, Instant now) {
        try {
            jdbc.update("INSERT INTO account_memberships(account_id,plan,expires_at,source,version_no,created_at,updated_at) VALUES(?,'FREE',NULL,?,0,?,?)",
                    accountId, source, now, now);
        } catch (DuplicateKeyException exists) {
            // The row is there already; the locked read below works on it.
        }
        List<Instant> locked = jdbc.query("SELECT plan,expires_at FROM account_memberships WHERE account_id=? FOR UPDATE",
                (rs, n) -> "PRO".equals(rs.getString("plan")) ? instant(rs, "expires_at") : null, accountId);
        Instant current = locked.isEmpty() ? null : locked.get(0);
        Instant base = current != null && current.isAfter(now) ? current : now;
        Instant expires = base.plus(Duration.ofDays(days));
        jdbc.update("UPDATE account_memberships SET plan='PRO',expires_at=?,source=?,version_no=version_no+1,updated_at=? WHERE account_id=?",
                expires, source, now, accountId);
        return expires;
    }

    /** A failed attempt is kept even though the request fails, so guessing hits the hourly limit. */
    private AppException failure(String accountId, Instant now, String reason, String message) {
        recordFailure(accountId, now);
        return AppException.user(reason, message);
    }

    private void recordFailure(String accountId, Instant now) {
        jdbc.update("INSERT INTO redeem_attempts(id,account_id,succeeded,created_at) VALUES(?,?,FALSE,?)", Ids.newId(), accountId, now);
    }

    private BatchView batch(String batchId, Instant now) {
        return jdbc.query("SELECT * FROM redeem_code_batches WHERE id=?", (rs, n) -> batchView(rs, now), batchId).stream().findFirst()
                .orElseThrow(() -> AppException.user("REDEEM_BATCH_NOT_FOUND", "兑换码批次不存在"));
    }

    private static BatchView batchView(ResultSet rs, Instant now) throws SQLException {
        int quantity = rs.getInt("quantity");
        int redeemed = rs.getInt("redeemed_count");
        Instant redeemBefore = instant(rs, "redeem_before");
        Instant revoked = instant(rs, "revoked_at");
        String status = revoked != null ? "REVOKED"
                : redeemed >= quantity ? "USED_UP"
                : redeemBefore != null && !redeemBefore.isAfter(now) ? "EXPIRED" : "ACTIVE";
        return new BatchView(rs.getString("id"), rs.getString("name"), rs.getString("channel"), rs.getString("plan"),
                rs.getInt("duration_days"), quantity, redeemed, redeemBefore, revoked, status, instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static void assertUser(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_MEMBERSHIP", "运营账号没有会员");
    }

    private static void assertAdmin(CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role())) throw AppException.forbidden("ADMIN_REQUIRED", "只有管理员可以管理兑换码");
    }

    private record CodeRow(String id, String batchId, String redeemedBy, String plan, int durationDays,
            Instant redeemBefore, Instant revokedAt, String batchName) {
    }

    public record Entitlements(String plan, int aiMonthlyUnits, int activeLinks) {
    }

    public record Price(String code, String label, int amountFen, int days) {
    }

    public record PlanCatalog(Entitlements free, Entitlements pro, List<Price> proPrices) {
    }

    /**
     * {@code plan} is what applies now; {@code expiresAt} is set while PRO runs, {@code expiredAt} once
     * it has lapsed (the page can then offer to renew).
     */
    public record MembershipView(String plan, Instant expiresAt, Instant expiredAt, Entitlements entitlements,
            Entitlements free, Entitlements pro, int activeLinks, List<EventView> events) {
    }

    public record EventView(String id, String type, String plan, int days, Instant expiresAt, String detail,
            Instant createdAt) {
    }

    public record BatchCommand(String name, String channel, Integer durationDays, Integer quantity, Instant redeemBefore) {
    }

    public record BatchView(String id, String name, String channel, String plan, int durationDays, int quantity,
            int redeemed, Instant redeemBefore, Instant revokedAt, String status, Instant createdAt) {
    }

    public record BatchCreated(BatchView batch, List<String> codes) {
    }
}
