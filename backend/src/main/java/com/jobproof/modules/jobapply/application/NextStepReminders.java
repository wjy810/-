package com.jobproof.modules.jobapply.application;

import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Next-step reminders (docs/phase2/01 §2): one in-app notice per application and scheduled time, sent
 * once the step is less than a day away. A step the user set a few minutes ago is not announced back to
 * them straight away. Each row is claimed before its notice goes out, so the sweep is safe to run on
 * several instances at once; moving the step to another time re-arms the reminder.
 */
@Service
public class NextStepReminders {

    static final Duration AHEAD = Duration.ofHours(24);
    static final Duration SETTLE = Duration.ofMinutes(10);
    private static final int BATCH = 200;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public NextStepReminders(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    /** Sends the reminders due at {@code now}; returns how many went out. */
    public int sendDue(Instant now) {
        List<Due> due = jdbc.query("SELECT id,account_id,company,position_title,next_step,next_step_at FROM job_applications "
                        + "WHERE next_step_at>? AND next_step_at<=? AND stage<>'CLOSED' AND updated_at<=? "
                        + "AND (next_step_reminded_for IS NULL OR next_step_reminded_for<>next_step_at) "
                        + "ORDER BY next_step_at LIMIT " + BATCH,
                (rs, n) -> new Due(rs.getString("id"), rs.getString("account_id"), rs.getString("company"),
                        rs.getString("position_title"), rs.getString("next_step"), rs.getTimestamp("next_step_at").toInstant()),
                now, now.plus(AHEAD), now.minus(SETTLE));
        int sent = 0;
        for (Due item : due) {
            int claimed = jdbc.update("UPDATE job_applications SET next_step_reminded_for=next_step_at WHERE id=? AND next_step_at=? "
                    + "AND (next_step_reminded_for IS NULL OR next_step_reminded_for<>next_step_at)", item.id(), Timestamp.from(item.at()));
            if (claimed == 0) continue;
            notifications.request(item.accountId(), NotificationType.REMINDER, "next-step:" + item.id() + ":" + item.at().toEpochMilli(),
                    title(item, now), "岗位：" + item.position() + "。打开投递详情看看对方读简历的情况，或者先模拟一场面试。",
                    "/applications?focus=" + item.id());
            sent++;
        }
        return sent;
    }

    static String title(Due item, Instant now) {
        String step = item.step() == null || item.step().isBlank() ? "下一步" : item.step().trim();
        String title = when(item.at(), now) + " · " + item.company() + " " + step;
        return title.length() > 200 ? title.substring(0, 199) + "…" : title;
    }

    /** "今天 14:00", "明天 09:30", or "10月21日 14:00" in China time. */
    static String when(Instant at, Instant now) {
        ZonedDateTime local = at.atZone(ZONE);
        LocalDate today = now.atZone(ZONE).toLocalDate();
        if (local.toLocalDate().equals(today)) return "今天 " + TIME.format(local);
        if (local.toLocalDate().equals(today.plusDays(1))) return "明天 " + TIME.format(local);
        return DAY_TIME.format(local);
    }

    record Due(String id, String accountId, String company, String position, String step, Instant at) {
    }
}
