package com.jobproof.modules.operations.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运营概览 (docs/phase2/04): the numbers an operator checks first. Who signs up and comes back, how far
 * seekers get (resume, application, a resume that was opened, Pro), redeem codes and AI use. Read-only
 * counts over the modules' tables; seekers only, so operator and test-admin accounts never skew them.
 */
@Service
public class OperationsOverviewService {

    static final int DAYS = 14;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String SEEKERS = "SELECT id FROM accounts WHERE role='SEEKER'";

    private final JdbcTemplate jdbc;
    private final ClockPort clock;

    public OperationsOverviewService(JdbcTemplate jdbc, ClockPort clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role())) {
            throw AppException.forbidden("ADMIN_REQUIRED", "只有管理员可以查看运营概览");
        }
        Instant now = clock.now();
        Instant week = now.minus(Duration.ofDays(7));
        Instant month = now.minus(Duration.ofDays(30));

        Users users = new Users(
                count("SELECT COUNT(*) FROM accounts WHERE role='SEEKER'"),
                count("SELECT COUNT(*) FROM accounts WHERE role='SEEKER' AND created_at>=?", week),
                count("SELECT COUNT(*) FROM accounts WHERE role='SEEKER' AND created_at>=?", month),
                count("SELECT COUNT(DISTINCT s.account_id) FROM sessions s JOIN accounts a ON a.id=s.account_id WHERE a.role='SEEKER' AND s.last_seen_at>=?", week),
                count("SELECT COUNT(DISTINCT s.account_id) FROM sessions s JOIN accounts a ON a.id=s.account_id WHERE a.role='SEEKER' AND s.last_seen_at>=?", month));

        long pro = count("SELECT COUNT(*) FROM account_memberships WHERE account_id IN (" + SEEKERS + ") AND plan='PRO' AND expires_at>?", now);
        Membership membership = new Membership(
                pro,
                share(pro, users.total()),
                count("SELECT COUNT(*) FROM membership_events WHERE plan='PRO' AND event_type IN ('REDEEMED','PURCHASED') AND account_id IN (" + SEEKERS + ") AND created_at>=?", month),
                count("SELECT COALESCE(SUM(quantity),0) FROM redeem_code_batches"),
                count("SELECT COALESCE(SUM(redeemed_count),0) FROM redeem_code_batches"),
                count("SELECT COUNT(*) FROM redeem_code_batches WHERE revoked_at IS NULL AND redeemed_count<quantity AND (redeem_before IS NULL OR redeem_before>?)", now));

        List<Module> modules = List.of(
                module("resumes", "简历", "resume_masters", "created_at", week),
                module("applications", "投递记录", "job_applications", "created_at", week),
                module("links", "专属链接", "resume_share_links", "created_at", week),
                new Module("opens", "简历被打开",
                        count("SELECT COUNT(*) FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id WHERE l.account_id IN (" + SEEKERS + ")"),
                        count("SELECT COUNT(*) FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id WHERE l.account_id IN (" + SEEKERS + ") AND v.started_at>=?", week)),
                module("matches", "岗位匹配", "job_match_tasks", "created_at", week),
                module("interviews", "模拟面试", "mock_interview_sessions", "created_at", week));

        String period = PERIOD.format(now.atZone(ZONE));
        Instant monthStart = now.atZone(ZONE).withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE).toInstant();
        long quotaUnits = count("SELECT COALESCE(SUM(q.used_units),0) FROM ai_quota_accounts q JOIN accounts a ON a.id=q.account_id WHERE a.role='SEEKER' AND q.period_key=?", period);
        long walletUnits = count("SELECT COALESCE(SUM(r.settled_units),0) FROM ai_quota_reservations r JOIN accounts a ON a.id=r.account_id WHERE a.role='SEEKER' AND r.payment_source='WALLET' AND r.status='SETTLED' AND r.updated_at>=?", monthStart);
        long personalUnits = count("SELECT COALESCE(SUM(r.settled_units),0) FROM ai_quota_reservations r JOIN accounts a ON a.id=r.account_id WHERE a.role='SEEKER' AND r.payment_source='PERSONAL' AND r.status='SETTLED' AND r.updated_at>=?", monthStart);
        Ai ai = new Ai(period,
                quotaUnits + walletUnits + personalUnits,
                count("SELECT COUNT(*) FROM accounts a WHERE a.role='SEEKER' AND (EXISTS(SELECT 1 FROM ai_quota_accounts q WHERE q.account_id=a.id AND q.period_key=? AND q.used_units>0) OR EXISTS(SELECT 1 FROM ai_quota_reservations r WHERE r.account_id=a.id AND r.settled_units>0 AND r.updated_at>=?))", period, monthStart),
                quotaUnits, walletUnits, personalUnits);

        List<FunnelStep> funnel = funnel(users.total());
        return new Overview(now, users, membership, modules, ai, funnel, days(now));
    }

    private List<FunnelStep> funnel(long registered) {
        long resumes = count("SELECT COUNT(DISTINCT account_id) FROM resume_masters WHERE account_id IN (" + SEEKERS + ")");
        long applied = count("SELECT COUNT(DISTINCT account_id) FROM job_applications WHERE account_id IN (" + SEEKERS + ")");
        long opened = count("SELECT COUNT(DISTINCT account_id) FROM resume_share_links WHERE view_count>0 AND account_id IN (" + SEEKERS + ")");
        long pro = count("SELECT COUNT(DISTINCT account_id) FROM membership_events WHERE plan='PRO' AND event_type IN ('REDEEMED','PURCHASED') AND account_id IN (" + SEEKERS + ")");
        List<FunnelStep> steps = new ArrayList<>();
        steps.add(new FunnelStep("registered", "注册", registered, null));
        steps.add(new FunnelStep("resume", "写了简历", resumes, share(resumes, registered)));
        steps.add(new FunnelStep("applied", "记录投递", applied, share(applied, resumes)));
        steps.add(new FunnelStep("opened", "简历被打开", opened, share(opened, applied)));
        steps.add(new FunnelStep("pro", "开通过专业版", pro, share(pro, registered)));
        return steps;
    }

    /** The last {@link #DAYS} days in China time, oldest first: sign-ups, new applications and resume opens. */
    private List<Day> days(Instant now) {
        LocalDate today = now.atZone(ZONE).toLocalDate();
        LocalDate first = today.minusDays(DAYS - 1);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < DAYS; i++) buckets.put(first.plusDays(i), new long[3]);
        bucket(buckets, "accounts", "created_at", "role='SEEKER'", 0);
        bucket(buckets, "job_applications", "created_at", "account_id IN (" + SEEKERS + ")", 1);
        bucket(buckets, "resume_share_views v JOIN resume_share_links l ON l.id=v.link_id", "v.started_at", "l.account_id IN (" + SEEKERS + ")", 2);
        List<Day> days = new ArrayList<>();
        buckets.forEach((day, values) -> days.add(new Day(day.toString(), values[0], values[1], values[2])));
        return days;
    }

    private void bucket(Map<LocalDate, long[]> buckets, String table, String column, String filter, int index) {
        // Explicit Shanghai boundaries work on MySQL and H2 without session-dependent DATE conversion.
        List<String> sums = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (LocalDate day : buckets.keySet()) {
            sums.add("COALESCE(SUM(CASE WHEN " + column + ">=? AND " + column + "<? THEN 1 ELSE 0 END),0)");
            args.add(Timestamp.from(day.atStartOfDay(ZONE).toInstant()));
            args.add(Timestamp.from(day.plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        args.add(args.get(0));
        jdbc.query("SELECT " + String.join(",", sums) + " FROM " + table + " WHERE " + filter + " AND " + column + ">=?", rs -> {
            int col = 1;
            for (long[] values : buckets.values()) values[index] = rs.getLong(col++);
        }, args.toArray());
    }

    private Module module(String key, String label, String table, String column, Instant since) {
        return new Module(key, label,
                count("SELECT COUNT(*) FROM " + table + " WHERE account_id IN (" + SEEKERS + ")"),
                count("SELECT COUNT(*) FROM " + table + " WHERE " + column + ">=? AND account_id IN (" + SEEKERS + ")", since));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** A share in whole percent, or null when there is nothing to divide by. */
    static Integer share(long part, long whole) {
        return whole > 0 ? (int) Math.round(part * 100.0 / whole) : null;
    }

    public record Overview(Instant generatedAt, Users users, Membership membership, List<Module> modules, Ai ai,
            List<FunnelStep> funnel, List<Day> days) {
    }

    public record Users(long total, long new7, long new30, long active7, long active30) {
    }

    public record Membership(long pro, Integer proShare, long redeemed30, long codesIssued, long codesRedeemed, long activeBatches) {
    }

    public record Module(String key, String label, long total, long last7) {
    }

    public record Ai(String period, long usedUnits, long accounts, long quotaUnits, long walletUnits, long personalUnits) {
    }

    public record FunnelStep(String key, String label, long accounts, Integer rate) {
    }

    public record Day(String day, long signups, long applications, long opens) {
    }
}
