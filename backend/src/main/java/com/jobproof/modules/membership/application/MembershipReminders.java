package com.jobproof.modules.membership.application;

import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Expiry notices (docs/phase2/02 §2): "专业版还有 N 天到期" once the end is a week away, and "专业版已到期"
 * once it has passed. Each expiry date is announced at most once per notice; extending the plan moves
 * the date and re-arms both. A plan that was opened or extended inside the last week (a 7-day code) gets
 * no early warning, only the notice when it ends.
 */
@Service
public class MembershipReminders {

    static final Duration NOTICE_AHEAD = Duration.ofDays(7);
    /** A plan that ended longer ago than this (for example while the sweep was off) is not announced late. */
    static final Duration ENDED_GRACE = Duration.ofDays(3);
    private static final int BATCH = 200;
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final MembershipService memberships;

    public MembershipReminders(JdbcTemplate jdbc, NotificationService notifications, MembershipService memberships) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.memberships = memberships;
    }

    /** Sends the notices due at {@code now}; returns how many went out. */
    public int sendDue(Instant now) {
        MembershipService.Entitlements free = memberships.catalog().free();
        int sent = 0;
        for (Row row : rows("expires_at>? AND expires_at<=? AND (expiry_notice_for IS NULL OR expiry_notice_for<>expires_at)",
                now, now.plus(NOTICE_AHEAD))) {
            boolean warnable = row.updatedAt().isBefore(row.expiresAt().minus(NOTICE_AHEAD));
            if (!claim(row, warnable ? "SOON" : "QUIET", "(expiry_notice_for IS NULL OR expiry_notice_for<>expires_at)") || !warnable) continue;
            long days = Math.max(1, (Duration.between(now, row.expiresAt()).toHours() + 23) / 24);
            notifications.request(row.accountId(), NotificationType.MEMBERSHIP, "membership-soon:" + row.expiresAt().toEpochMilli(),
                    "专业版还有 " + days + " 天到期",
                    "到期时间 " + DAY_TIME.format(row.expiresAt()) + "。到期后回到免费版：每月 " + free.aiMonthlyUnits() + " 次 AI，同时保留 "
                            + free.activeLinks() + " 条有效链接，简历和投递记录都会保留。再兑换一个码会在到期日之后顺延。",
                    "/account/membership");
            sent++;
        }
        for (Row row : rows("expires_at<=? AND expires_at>? AND (expiry_notice_for IS NULL OR expiry_notice_for<>expires_at OR expiry_notice<>'ENDED')",
                now, now.minus(ENDED_GRACE))) {
            if (!claim(row, "ENDED", "(expiry_notice_for IS NULL OR expiry_notice_for<>expires_at OR expiry_notice<>'ENDED')")) continue;
            notifications.request(row.accountId(), NotificationType.MEMBERSHIP, "membership-ended:" + row.expiresAt().toEpochMilli(),
                    "专业版已到期，已回到免费版",
                    "现在每月 " + free.aiMonthlyUnits() + " 次 AI，同时保留 " + free.activeLinks()
                            + " 条有效链接；已经发出的链接在各自有效期内继续可用。兑换新码即可恢复专业版。",
                    "/account/membership");
            sent++;
        }
        return sent;
    }

    private List<Row> rows(String condition, Instant from, Instant to) {
        return jdbc.query("SELECT account_id,expires_at,updated_at FROM account_memberships WHERE plan='PRO' AND " + condition
                        + " ORDER BY expires_at LIMIT " + BATCH,
                (rs, n) -> new Row(rs.getString("account_id"), rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                from, to);
    }

    /** Marks the notice as sent for this expiry; false when another sweep, or a renewal, got there first. */
    private boolean claim(Row row, String notice, String unsent) {
        return jdbc.update("UPDATE account_memberships SET expiry_notice=?,expiry_notice_for=expires_at WHERE account_id=? AND plan='PRO' AND expires_at=? AND "
                + unsent, notice, row.accountId(), Timestamp.from(row.expiresAt())) == 1;
    }

    private record Row(String accountId, Instant expiresAt, Instant updatedAt) {
    }
}
