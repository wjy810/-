package com.jobproof.modules.jobapply.application;

import com.jobproof.modules.jobapply.domain.ApplicationStage;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 求职周报 (docs/phase2/06): on Monday morning, one notice per seeker who tracks applications, saying what
 * happened last week (applications recorded, resumes opened and by whom, moves to a test, an interview
 * or an offer) and what is coming this week. Accounts with nothing to report get nothing. Each account
 * is claimed for the week before its notice goes out, so several instances never send twice.
 */
@Service
public class WeeklyDigest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** Digests go out from Monday 09:00; a week whose Monday was missed is caught up until Wednesday. */
    private static final Duration SEND_FROM = Duration.ofHours(9);
    private static final Duration SEND_UNTIL = Duration.ofDays(3);
    private static final int BATCH = 200;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZONE);

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public WeeklyDigest(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    /** Handles one batch of the digests due at {@code now}; returns how many accounts it settled (sent or nothing to say). */
    public int sendDue(Instant now) {
        ZonedDateTime weekStart = now.atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE);
        Instant sendFrom = weekStart.toInstant().plus(SEND_FROM);
        if (now.isBefore(sendFrom) || !now.isBefore(weekStart.toInstant().plus(SEND_UNTIL))) return 0;
        Instant to = weekStart.toInstant();
        Instant from = weekStart.minusWeeks(1).toInstant();
        String week = weekKey(weekStart.minusWeeks(1).toLocalDate());
        List<String> accounts = jdbc.queryForList("SELECT DISTINCT a.account_id FROM job_applications a JOIN accounts u ON u.id=a.account_id "
                        + "LEFT JOIN weekly_digests d ON d.account_id=a.account_id AND d.week_key=? "
                        + "WHERE u.role='SEEKER' AND u.status='ACTIVE' AND d.account_id IS NULL LIMIT " + BATCH,
                String.class, week);
        int settled = 0;
        for (String accountId : accounts) {
            Summary summary = summarize(accountId, from, to, now);
            try {
                jdbc.update("INSERT INTO weekly_digests(account_id,week_key,sent,created_at) VALUES(?,?,?,?)",
                        accountId, week, !summary.empty(), now);
            } catch (DuplicateKeyException claimed) {
                continue;
            }
            settled++;
            if (summary.empty()) continue;
            notifications.request(accountId, NotificationType.WEEKLY_DIGEST, "weekly-digest:" + week,
                    summary.title(), summary.body(), "/applications");
        }
        return settled;
    }

    Summary summarize(String accountId, Instant from, Instant to, Instant now) {
        Timestamp start = Timestamp.from(from);
        Timestamp end = Timestamp.from(to);
        // 投递 is the day it went out, not the day it was typed in; 想投 has not gone out at all.
        int applied = count("SELECT COUNT(*) FROM job_applications WHERE account_id=? AND archived_at IS NULL AND furthest_stage<>'WISHLIST' "
                + "AND applied_on>=? AND applied_on<?", accountId,
                java.sql.Date.valueOf(from.atZone(ZONE).toLocalDate()), java.sql.Date.valueOf(to.atZone(ZONE).toLocalDate()));
        // Archived applications are out of the digest, the openings of their links too.
        int opens = count("SELECT COUNT(*) FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id "
                + "LEFT JOIN job_applications a ON a.id=l.application_id "
                + "WHERE l.account_id=? AND (l.application_id IS NULL OR a.archived_at IS NULL) AND v.started_at>=? AND v.started_at<?",
                accountId, start, end);
        List<String> openedBy = jdbc.queryForList("SELECT DISTINCT a.company FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id "
                + "JOIN job_applications a ON a.id=l.application_id WHERE l.account_id=? AND a.archived_at IS NULL AND v.started_at>=? AND v.started_at<? ORDER BY a.company",
                String.class, accountId, start, end);
        // 进入下一轮: applications that moved forward into a test, an interview or an offer, each counted once.
        java.util.Set<String> advancedIds = new java.util.HashSet<>();
        jdbc.query("SELECT application_id,from_stage,to_stage FROM job_application_events WHERE account_id=? AND event_type=? "
                        + "AND to_stage IN ('ASSESSMENT','INTERVIEW','OFFER') AND created_at>=? AND created_at<?",
                (java.sql.ResultSet rs) -> {
                    if (forward(rs.getString("from_stage"), rs.getString("to_stage"))) advancedIds.add(rs.getString("application_id"));
                }, accountId, ApplicationTimeline.STAGE_CHANGED, start, end);
        int advanced = advancedIds.size();
        List<String> upcoming = jdbc.query("SELECT company,next_step,next_step_at FROM job_applications WHERE account_id=? AND stage<>'CLOSED' AND archived_at IS NULL "
                        + "AND next_step_at>=? AND next_step_at<? ORDER BY next_step_at",
                (rs, n) -> WHEN.format(rs.getTimestamp("next_step_at").toInstant()) + " " + rs.getString("company") + " "
                        + blank(rs.getString("next_step"), "下一步"),
                accountId, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofDays(7))));
        return new Summary(applied, opens, openedBy, advanced, upcoming);
    }

    static boolean forward(String fromStage, String toStage) {
        try {
            ApplicationStage to = ApplicationStage.parse(toStage);
            if (fromStage == null) return true;
            ApplicationStage from = ApplicationStage.parse(fromStage);
            // Reopening a closed application corrects the record; it is not a new round.
            return from.open() && from.rank() < to.rank();
        } catch (RuntimeException unknownStage) {
            return false;
        }
    }

    /** "2026-W43": the ISO week the digest summarises. */
    static String weekKey(LocalDate day) {
        return "%d-W%02d".formatted(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    record Summary(int applied, int opens, List<String> openedBy, int advanced, List<String> upcoming) {

        boolean empty() {
            return applied == 0 && opens == 0 && advanced == 0 && upcoming.isEmpty();
        }

        /** "求职周报：上周投递 3 家，简历被打开 5 次，进入下一轮 1 次". */
        String title() {
            List<String> parts = new ArrayList<>();
            if (applied > 0) parts.add("上周投递 " + applied + " 家");
            if (opens > 0) parts.add("简历被打开 " + opens + " 次");
            if (advanced > 0) parts.add("进入下一轮 " + advanced + " 次");
            if (parts.isEmpty()) parts.add("本周有 " + upcoming.size() + " 个安排");
            return "求职周报：" + String.join("，", parts);
        }

        String body() {
            List<String> lines = new ArrayList<>();
            if (!openedBy.isEmpty()) {
                String names = String.join("、", openedBy.subList(0, Math.min(3, openedBy.size())));
                lines.add(names + (openedBy.size() > 3 ? " 等 " + openedBy.size() + " 家公司" : "") + "打开了你的简历");
            }
            if (!upcoming.isEmpty()) {
                lines.add("本周安排：" + String.join("、", upcoming.subList(0, Math.min(3, upcoming.size())))
                        + (upcoming.size() > 3 ? " 等 " + upcoming.size() + " 项" : ""));
            } else if (!openedBy.isEmpty()) {
                lines.add("本周还没有安排面试或笔试，可以跟进一下打开过简历的公司");
            }
            if (lines.isEmpty()) lines.add("打开投递追踪，看看各家公司的进展");
            String body = String.join("。", lines) + "。";
            return body.length() > 1000 ? body.substring(0, 999) + "…" : body;
        }
    }
}
